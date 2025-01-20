package org.example;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ibm.mq.MQException;
import com.ibm.mq.MQQueueManager;
import com.ibm.mq.constants.CMQC;
import com.ibm.mq.constants.CMQCFC;
import com.ibm.mq.headers.MQDataException;
import com.ibm.mq.headers.pcf.PCFMessage;
import com.ibm.mq.headers.pcf.PCFMessageAgent;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;

public class CreateQConfig {

    static String qmgrConfigResourceName = "qmgr_config.json";
    static String qConfigFSPath = System.getProperty("user.home") + File.separator + "q_config.json";
    static Hashtable<String, Object> qmgrProperties = new Hashtable<>();
    static String qmgrArrayName = "qmgrList";
    static ObjectNode qConfigNode = null;
    static ObjectNode qmgrConfigNode = null;

    public static void main(String[] args) {
        try {
            createQConfig(args[0]);
        } catch (ArrayIndexOutOfBoundsException e) {
            System.out.println("Error: missing argument. Usage: CreateQConfig.java <environment>");
        }
    }

    public static void createQConfig(String env) {
        List<String> qmgrNames = new ArrayList<>();
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            qmgrConfigNode = (ObjectNode) readJSONFromResource(env + "/" + qmgrConfigResourceName);
        } catch (RuntimeException e) {
            throw new RuntimeException(e);
        }
        Iterator<String> iterator = qmgrConfigNode.fieldNames();
        iterator.forEachRemaining(fieldName -> qmgrNames.add(fieldName));
        try {
            qConfigNode = (ObjectNode) objectMapper.readTree(readFileFromFS(qConfigFSPath));
            System.out.println("Queueconfig already exist, file loaded.");
        } catch (RuntimeException | IOException e) {
            qConfigNode = objectMapper.createObjectNode();
            System.out.println("Queueconfig not found, new file will be created.");
        }
        for (String qmgrName : qmgrNames) {
            System.out.println("Getting queuelist from " + qmgrName + "...");
            try {
                List<String> qNames = getQNames(qmgrName);
                for (String qName : qNames) {
                    if (qConfigNode.has(qName)) {
                        ArrayNode qmgrListNode = (ArrayNode) qConfigNode.at("/" + qName).get(qmgrArrayName);
                        List<String> qmgrList = new ArrayList<>();
                        for (JsonNode qmgrNode : qmgrListNode) {
                            qmgrList.add(qmgrNode.asText());
                        }
                        if (!qmgrList.contains(qmgrName)) {
                            qmgrListNode.add(qmgrName);
                        }
                    } else {
                        ObjectNode childNode = objectMapper.createObjectNode();
                        qConfigNode.set(qName, childNode);
                        ArrayList<String> arrayList = new ArrayList<>();
                        arrayList.add(qmgrName);
                        ArrayNode arrayNode = objectMapper.valueToTree(arrayList);
                        childNode.putArray(qmgrArrayName).addAll(arrayNode);
                    }
                }
            } catch (RuntimeException e) {
                System.out.println("Error: can't get queuelist from " + qmgrName + ".");
            }
        }
        if (!qConfigNode.isEmpty()) {
            try {
                writeFileToFS(qConfigFSPath, prettyPrintJSON(objectMapper.writeValueAsString(qConfigNode)));
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static List<String> getQNames(String qmgrName) {
        JsonNode connParams = qmgrConfigNode.get(qmgrName);
        qmgrProperties.put(CMQC.HOST_NAME_PROPERTY, connParams.get("host").asText());
        qmgrProperties.put(CMQC.PORT_PROPERTY, connParams.get("port").asInt());
        qmgrProperties.put(CMQC.CHANNEL_PROPERTY, connParams.get("channel").asText());
        qmgrProperties.put(CMQC.USER_ID_PROPERTY, connParams.get("user_id").asText());
        qmgrProperties.put(CMQC.PASSWORD_PROPERTY, connParams.get("password").asText());
        List<String> qNames = new ArrayList<>();
        try {
            MQQueueManager queueManager = new MQQueueManager(qmgrName, qmgrProperties);
            PCFMessageAgent pcfMessageAgent = new PCFMessageAgent(queueManager);
            PCFMessage request = new PCFMessage(CMQCFC.MQCMD_INQUIRE_Q);
            request.addParameter(CMQC.MQCA_Q_NAME, "*");
            request.addParameter(CMQC.MQIA_Q_TYPE, CMQC.MQQT_LOCAL);
            request.addParameter(CMQCFC.MQIACF_Q_ATTRS, new int[]{CMQC.MQCA_Q_NAME});
            PCFMessage[] responses = pcfMessageAgent.send(request);
            for (PCFMessage response : responses) {
                String qName = response.getStringParameterValue(CMQC.MQCA_Q_NAME);
                qNames.add(qName.trim());
            }
            pcfMessageAgent.disconnect();
            queueManager.disconnect();
            return qNames;
        } catch (MQException | MQDataException | IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static JsonNode readJSONFromResource(String resourceName) {
        InputStream inputStream = Thread.currentThread().getContextClassLoader().getResourceAsStream(resourceName);
        try {
            return new ObjectMapper().readTree(inputStream);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static String prettyPrintJSON(String unformattedJSON) {
        try {
            return new ObjectMapper().readTree(unformattedJSON).toPrettyString();
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }

    public static String readFileFromFS(String filePath) throws IOException {
        Path path = Path.of(filePath);
        return Files.readString(path, StandardCharsets.UTF_8);
    }

    public static void writeFileToFS(String filePath, String fileContent) {
        try {
            Files.writeString(Path.of(filePath), fileContent);
            System.out.print("\n---> " + filePath + "\n");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}