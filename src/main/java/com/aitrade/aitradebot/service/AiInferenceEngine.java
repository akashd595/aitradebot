package com.aitrade.aitradebot.service;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtSession;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.util.Collections;
import java.util.Map;

@Slf4j
@Service
public class AiInferenceEngine {
    private OrtEnvironment env;
    private OrtSession session;
    private boolean isModelLoaded = false;

    @PostConstruct
    public void init() {
        try {
            env = OrtEnvironment.getEnvironment();
            ClassPathResource resource = new ClassPathResource("models/xgboost_swing_model.onnx");
            if (resource.exists()) {
                byte[] modelBytes = resource.getInputStream().readAllBytes();
                session = env.createSession(modelBytes, new OrtSession.SessionOptions());
                isModelLoaded = true;
                log.info("Successfully loaded ONNX model: xgboost_swing_model.onnx");
            } else {
                log.warn("ONNX model file not found in src/main/resources/models/. AI Inference will be bypassed.");
            }
        } catch (Exception e) {
            log.error("Failed to initialize ONNX environment or load model: {}", e.getMessage());
        }
    }

    public boolean confirmSignal(float rsi, float distanceToEma20, float distanceToEma200, float volDelta) {
        if (!isModelLoaded || session == null) {
            log.warn("Model is not loaded. Defaulting AI confirmation to false.");
            return false;
        }

        try {
            float[][] features = new float[][]{{rsi, distanceToEma20, distanceToEma200, volDelta}};
            try (OnnxTensor tensor = OnnxTensor.createTensor(env, features)) {
                String inputName = session.getInputNames().iterator().next();
                
                try (OrtSession.Result result = session.run(Collections.singletonMap(inputName, tensor))) {
                    Object value = result.get(1).getValue(); 
                    
                    log.info("AI Inference Features -> RSI: {}, distEMA20: {}, distEMA200: {}, volDelta: {}",
                            rsi, distanceToEma20, distanceToEma200, volDelta);
                            
                    if (value instanceof java.util.List) {
                        java.util.List<?> list = (java.util.List<?>) value;
                        if (!list.isEmpty() && list.get(0) instanceof Map) {
                            Map<?, ?> probMap = (Map<?, ?>) list.get(0);
                            Object probObj = probMap.get(1L);
                            if (probObj instanceof Float) {
                                float probBuy = (Float) probObj;
                                log.info("AI Predicted BUY Probability: {}", probBuy);
                                return probBuy >= 0.65f;
                            }
                        }
                    }
                    
                    if (value instanceof float[][]) {
                        float[][] probs = (float[][]) value;
                        float probBuy = probs[0][1];
                        log.info("AI Predicted BUY Probability: {}", probBuy);
                        return probBuy >= 0.65f;
                    }

                    log.warn("Unrecognized output format from ONNX model.");
                    return false;
                }
            }
        } catch (Exception e) {
            log.error("Error during AI inference: {}", e.getMessage(), e);
            return false;
        }
    }
}
