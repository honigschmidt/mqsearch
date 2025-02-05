package org.example;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ibm.mq.*;
import com.ibm.mq.constants.CMQC;
import com.ibm.mq.constants.MQConstants;
import org.dom4j.DocumentHelper;
import org.dom4j.io.OutputFormat;
import org.dom4j.io.XMLWriter;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public final class MQSearch {

    static String queueName = "";
    static int queueDepth = 0;
    static String queueManagerName = "";
    static List<String> queueNameList = new ArrayList<>();
    static List<String> queueManagerNameList = new ArrayList<>();
    static String defEnv = "DEV";
    static String qmgrConfigResourceName = "qmgr_config.json";
    static String qConfigResourceName = "q_config.json";
    static JsonNode qmgrConfig = null;
    static JsonNode qConfig = null;
    static String env = null;
    static String qmgrArrayName = "qmgrList";
    static Hashtable<String, Object> qmgrProperties = new Hashtable<>();
    static List<String> searchParameterList = new ArrayList<>();
    static int searchParameterListMaxLength = 4;
    static LocalDateTime searchDateTimeFrom = null;
    static LocalDateTime searchDateTimeTo = null;
    static boolean writeMessageToFile = false;
    static boolean browseError = false;
    static int messageCounter = 1;
    static int foundMessageCounter = 0;
    static int addCounter = 0;
    static boolean foundInQueue;
    static String release = "1.09 04-02-2025";
    static String clientLib = "9.4.1.1";
    static String about = "https://github.com/honigschmidt/mqsearch";
    static String logo = "\n" +
            " __  __  ____   _____                     _     \n" +
            "|  \\/  |/ __ \\ / ____|                   | |    \n" +
            "| \\  / | |  | | (___   ___  __ _ _ __ ___| |__  \n" +
            "| |\\/| | |  | |\\___ \\ / _ \\/ _` | '__/ __| '_ \\ \n" +
            "| |  | | |__| |____) |  __/ (_| | | | (__| | | |\n" +
            "|_|  |_|\\___\\_\\_____/ \\___|\\__,_|_|  \\___|_| |_|\n";
    static final String XMLFileExtension = ".xml";
    static final String JSONFileExtension = ".json";
    static final String rawFileExtension = ".txt";
    static String workingDir = System.getProperty("user.home") + File.separator + "MQSearchData";
    static ObjectNode statNode = new ObjectMapper().createObjectNode();

    public static void main(String[] args) {
        env = defEnv;
        setEnvironment(env);
        consoleApplication();
    }

    public static void consoleApplication() {

        while (true) {
            System.out.print("\n");
            System.out.print(logo);
            System.out.print("---");
            System.out.print("\nEnvironment: " + env);
            System.out.print("\n---");
            System.out.print("\nQueues on searchlist");
            if (queueNameList.isEmpty()) {
                System.out.print(": none");
            } else {
                System.out.print(" (" + queueNameList.size() + "): ");
                for (int i = 0; i < queueNameList.size(); i++) {
                    System.out.print(queueNameList.get(i));
                    if (i + 1 != queueNameList.size()) {
                        if (!queueNameList.get(i + 1).isBlank()) {
                            System.out.print(", ");
                        }
                    }
                }
            }
            System.out.print(
                    "\n---" +
                    "\n[a] Add queues to searchlist" +
                    "\n[c] Clear searchlist" +
                    "\n[l] Load queuenames from file" +
                    "\n[s] Search for messages" +
                    "\n[e] Change environment" +
                    "\n[t] Test connection to queuemanager" +
                    "\n[m] Load test messages" +
                    "\n[i] About" +
                    "\n[x] Exit"
            );
            System.out.print("\nSelect an option and press [ENTER]: ");
            String userInput = getUserInput();
            switch (userInput) {
                case "a":
                    selectQueues();
                    break;
                case "c":
                    clearSearchlist();
                    break;
                case "l":
                    loadQListFromFile();
                    break;
                case "s":
                    searchMessage();
                    break;
                case "e":
                    changeEnvironmentOption();
                    break;
                case "t":
                    testConnection();
                    break;
                case "m":
                    loadTest();
                    break;
                case "i":
                    about();
                    break;
                case "x":
                    exitApplication();
                    break;
                default:
                    System.out.print("\nError: invalid selection.");
                    break;
            }
        }
    }

    public static void exitApplication() {
        System.out.print("\nBye!");
        System.exit(0);
    }

    public static void changeEnvironmentOption() {
        while (true) {
            System.out.print("\nEnter environment name and press [ENTER]. Leave blank to cancel: ");
            String env = getUserInput();
            if (env.isBlank()) {
                break;
            }
            try {
                setEnvironment(env);
                break;
            } catch (RuntimeException e) {
                System.out.print("\nError: invalid environment.");
            }
        }
    }

    public static void setEnvironment(String env) {
        qmgrConfig = readJSONFromResource(env + "/" + qmgrConfigResourceName);
        qConfig = readJSONFromResource(env + "/" + qConfigResourceName);
        queueName = null;
        queueManagerName = null;
        queueNameList.clear();
        queueManagerNameList.clear();
        MQSearch.env = env;
    }

    public static void loadTest() {
        queueManagerName = "QM1";
        queueName = "DEV.QUEUE.1";
        queueNameList.add("DEV.QUEUE.1");
        setConnectionProperties();
        String testMessage1 = readStringFromResource("TestMessage1.json");
        String testMessage2 = readStringFromResource("TestMessage2.xml");
        String testMessage3 = readStringFromResource("TestMessage3.txt");
        List<String> messageList = new ArrayList<>();
        messageList.add(testMessage1);
        messageList.add(testMessage2);
        messageList.add(testMessage3);
        for (String message : messageList) {
            MQMessage mqMessage = new MQMessage();
            try {
                mqMessage.writeString(message);
                putMessage(mqMessage);
                mqMessage.clearMessage();
                mqMessage.correlationId = MQConstants.MQCI_NONE;
                mqMessage.messageId = MQConstants.MQMI_NONE;
            } catch (IOException | MQException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static void loadQListFromFile() {
        ArrayList<String> qNames = new ArrayList<>();
        while (true) {
            System.out.print("\nEnter filename to load or leave blank to cancel and press [ENTER]. File must be in the %HOME%/MQSearchData directory: ");
            String fileName = getUserInput();
            if (fileName.isBlank()) {
                break;
            }
            String filePath = workingDir + File.separator + fileName;
            try {
                String fileContent = readFileFromFS(filePath);
                Scanner scanner = new Scanner(fileContent);
                while (scanner.hasNextLine()) {
                    String line = scanner.nextLine();
                    if (!line.isBlank() && !line.contains("#") && line.length() >= 6) {
                        if (line.charAt(6) == ' ') {
                            qNames.add(line.substring(7));
                        } else {
                            qNames.add(line);
                        }
                    }
                }
                scanner.close();
                break;
            } catch (IOException e) {
                System.out.print("\nError: invalid filename.");
            }
        }
        ArrayList<String> qNamesTemp = new ArrayList<>();
        for (String qName : qNames) {
            if (qConfig.has(qName.trim())) {
                qNamesTemp.add(qName.trim());
            } else {
                System.out.print("\nError: unknown queuename in file: " + qName + ".");
            }
        }
        if (!qNamesTemp.isEmpty()) {
            queueNameList.clear();
            for (String qName : qNamesTemp) {
                queueNameList.add(qName);
            }
        } else {
            System.out.println("\nError: no queuename can be added from the selected file.");
        }
    }

    public static void testConnection() {
        System.out.print("\nEnter name of the queuemanager and press [ENTER]: ");
        queueManagerName = getUserInput();
        try {
            System.out.print("\nTrying to connect to " + queueManagerName);
            setConnectionProperties();
            System.out.print("\nHost: " + qmgrProperties.get(CMQC.HOST_NAME_PROPERTY));
            System.out.print("\nPort: " + qmgrProperties.get(CMQC.PORT_PROPERTY));
            System.out.print("\nChannel: " + qmgrProperties.get(CMQC.CHANNEL_PROPERTY));
            System.out.print("\nUser ID: " + qmgrProperties.get(CMQC.USER_ID_PROPERTY));
            System.out.print("\nPassword: " + qmgrProperties.get(CMQC.PASSWORD_PROPERTY));
            if (qmgrReachable()) {
                System.out.print("\n\nSuccessfully connected to the queuemanager.");
            }
        } catch (NullPointerException e) {
            System.out.print("\n\nError: unknown queuemanager.");
        }
    }

    public static void selectQueues() {
        addCounter = 0;
        System.out.print("Enter a queuename or a part of it and press [ENTER]: ");
        String qName = getUserInput().toUpperCase();
        if (!qName.isBlank()) {
            Iterator<String> qConfigIterator = qConfig.fieldNames();
            qConfigIterator.forEachRemaining(fieldName -> {
                if (fieldName.contains(qName) && !queueNameList.contains(fieldName)) {
                    System.out.print("\n+ " + fieldName);
                    queueNameList.add(fieldName);
                    addCounter ++;
                }
            });
            System.out.println();
        }
        System.out.print("\nAdded " + addCounter + " queue(s) to searchlist.");
    }

    public static void clearSearchlist() {
        queueNameList.clear();
    }

    public static void searchMessage() {
        statNode.removeAll();
        if (queueNameList.isEmpty()) {
            System.out.print("\nError: no queues are selected.");
        } else {
            searchParameterList.clear();
            for (int i = 0; i < searchParameterListMaxLength; i++) {
                System.out.print("Enter a search parameter or leave empty and press [ENTER]: ");
                String userInput = getUserInput();
                if (!userInput.isBlank()) {
                    searchParameterList.add(userInput);
                }
            }
            DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("HHmmddMMyyyy");
            while (true) {
                System.out.print("\nEnter search dates from/to (hhmmddmmyyyy-hhmmddmmyyyy) or leave empty and press [ENTER]. Usable shorthands: [last15], [lasthour], [today]: ");
                String timeStamp = getUserInput();
                if (timeStamp.isBlank()) {
                    searchDateTimeFrom = null;
                    searchDateTimeTo = null;
                    break;
                }
                if (timeStamp.equals("last15")) {
                    searchDateTimeFrom = LocalDateTime.now().minusMinutes(15);
                    searchDateTimeTo = LocalDateTime.now();
                    break;
                }
                if (timeStamp.equals("lasthour")) {
                    searchDateTimeFrom = LocalDateTime.now().minusHours(1);
                    searchDateTimeTo = LocalDateTime.now();
                    break;
                }
                if (timeStamp.equals("today")) {
                    searchDateTimeFrom = LocalDate.now().atStartOfDay();
                    searchDateTimeTo = LocalDateTime.now();
                    break;
                }
                int fromStart = 0;
                int fromEnd = 12;
                int toStart = 13;
                int toEnd = 25;
                try {
                    searchDateTimeFrom = LocalDateTime.parse(timeStamp.substring(fromStart, fromEnd), dateTimeFormatter);
                    searchDateTimeTo = LocalDateTime.parse(timeStamp.substring(toStart, toEnd), dateTimeFormatter);
                    if (searchDateTimeFrom.isBefore(searchDateTimeTo)) {
                        break;
                    } else throw new RuntimeException();
                } catch (RuntimeException e) {
                    System.out.print("\nError: invalid timestamp.");
                }
            }
            System.out.print("\nSave messages to disk? Press [ENTER] to yes or any other key to no: ");
            String userInput = getUserInput();
            writeMessageToFile = userInput.isBlank();
            messageCounter = 1;
            foundMessageCounter = 0;
            LocalDateTime searchStart = LocalDateTime.now();
            for (String qName : queueNameList) {
                queueManagerNameList.clear();
                queueName = qName;
                JsonNode qmgrListNode = qConfig.at("/" + queueName).get(qmgrArrayName);
                for (JsonNode qmgrNode : qmgrListNode) {
                    queueManagerNameList.add(qmgrNode.asText());
                }
                for (String qmgr : queueManagerNameList) {
                    queueManagerName = qmgr;
                    setConnectionProperties();
                    try {
                        queueDepth = getQueueDepth();
                    } catch (MQException e) {
                        handleMQException(e);
                    }
                    System.out.print("\nChecking " + queueName + " (" + queueManagerName + "). Messages in queue: " + queueDepth + ".\n");
                    if (queueDepth != 0) {
                        browseQueue();
                        if (!foundInQueue && !browseError) {
                            System.out.println("\nNo matching message(s) found in the queue " + queueName + ".");
                        }
                    } else {
                        String statNodeKey = queueName + " (" + queueManagerName + ")";
                        statNode.put(statNodeKey, "queue empty");
                    }
                }
            }
            DateTimeFormatter logDateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss dd-MM-yyyy");
            System.out.print(
                "\n---" +
                "\nEnvironment: " + env +
                "\nSearch started at " + searchStart.format(logDateTimeFormatter) +
                "\nSearch finished at " + LocalDateTime.now().format(logDateTimeFormatter) +
                "\nSearch parameters: " + (searchParameterList.isEmpty() ? "n/a" : searchParameterList) +
                "\nSearch from: " + (Objects.isNull(searchDateTimeFrom) ? "n/a" : searchDateTimeFrom.format(dateTimeFormatter)) +
                "\nSearch to: " + (Objects.isNull(searchDateTimeTo) ? "n/a" : searchDateTimeTo.format(dateTimeFormatter)) +
                "\nSave to disk: " + (writeMessageToFile ? "yes" : "no"));
            System.out.print("\n\nMatching message(s) found per queue:\n");
            Iterator<String> statNodeIterator = statNode.fieldNames();
            statNodeIterator.forEachRemaining(qName -> {
                System.out.print("\n" + qName + ": " + statNode.get(qName).asText());
            });
            System.out.print("\n\nTotal of " + foundMessageCounter + " matching message(s) found.");
            System.out.println("\n---");
        }
    }

    public static int getQueueDepth() throws MQException {
        MQQueueManager queueManager = new MQQueueManager(queueManagerName, qmgrProperties);
        int queueOpenOptions = MQConstants.MQOO_INQUIRE;
        MQQueue queue = queueManager.accessQueue(queueName, queueOpenOptions);
        int queueDepth = queue.getCurrentDepth();
        queue.close();
        queueManager.disconnect();
        return queueDepth;
    }

    public static void putMessage(MQMessage mqMessage) throws MQException {
        MQQueueManager queueManager = new MQQueueManager(queueManagerName, qmgrProperties);
        int queueOpenOptions = MQConstants.MQOO_INPUT_AS_Q_DEF | MQConstants.MQOO_OUTPUT;
        MQPutMessageOptions putMessageOptions = new MQPutMessageOptions();
        MQQueue queue = queueManager.accessQueue(queueName, queueOpenOptions);
        queue.put(mqMessage, putMessageOptions);
        queue.close();
        queueManager.disconnect();
    }

    public static MQMessage getMessage() throws MQException {
        MQQueueManager queueManager = new MQQueueManager(queueManagerName, qmgrProperties);
        int queueOpenOptions = MQConstants.MQOO_INPUT_AS_Q_DEF | MQConstants.MQOO_OUTPUT;
        MQQueue queue = queueManager.accessQueue(queueName, queueOpenOptions);
        MQGetMessageOptions getMessageOptions = new MQGetMessageOptions();
        MQMessage mqMessage = new MQMessage();
        queue.get(mqMessage, getMessageOptions);
        queue.close();
        queueManager.disconnect();
        return mqMessage;
    }

    public static String getMessagePayload(MQMessage mqMessage) {
        try {
            byte[] byteMessage = new byte[mqMessage.getMessageLength()];
            mqMessage.readFully(byteMessage);
            return new String(byteMessage);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static boolean qmgrReachable() {
        try {
            MQQueueManager queueManager = new MQQueueManager(queueManagerName, qmgrProperties);
            queueManager.disconnect();
            return true;
        } catch (MQException e) {
            handleMQException(e);
            return false;
        }
    }

    public static void browseQueue() {
        foundInQueue = false;
        browseError = false;
        int getCount = 1;
        try {
            int qDepth = getQueueDepth();
        } catch (MQException e) {
            handleMQException(e);
        }
        try {
            boolean isFirstMessage = true;
            boolean isDone = false;
            MQQueueManager queueManager = new MQQueueManager(queueManagerName, qmgrProperties);
            int queueOpenOptions = MQConstants.MQOO_BROWSE;
            MQQueue queue = queueManager.accessQueue(queueName, queueOpenOptions);
            MQMessage mqMessage = new MQMessage();
            MQGetMessageOptions getMessageOptions = new MQGetMessageOptions();
            while (!isDone) {
                try {
                    if (isFirstMessage) {
                        getMessageOptions.options = MQConstants.MQGMO_BROWSE_FIRST + MQConstants.MQGMO_WAIT;
                        isFirstMessage = false;
                    } else {
                        getMessageOptions.options = MQConstants.MQGMO_BROWSE_NEXT + MQConstants.MQGMO_WAIT;
                    }
                    queue.get(mqMessage, getMessageOptions);
                    processMessage(mqMessage);
                    mqMessage.clearMessage();
                    mqMessage.correlationId = MQConstants.MQCI_NONE;
                    mqMessage.messageId = MQConstants.MQMI_NONE;
                } catch (MQException e) {
                    if (e.reasonCode == 2033) {
                        isDone = true;
                    } else handleMQException(e);
                }
            }
            queue.close();
            queueManager.disconnect();
        } catch (MQException e) {
            handleMQException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void setConnectionProperties() {
        JsonNode connParams = qmgrConfig.get(queueManagerName);
        qmgrProperties.put(CMQC.HOST_NAME_PROPERTY, connParams.get("host").asText());
        qmgrProperties.put(CMQC.PORT_PROPERTY, connParams.get("port").asInt());
        qmgrProperties.put(CMQC.CHANNEL_PROPERTY, connParams.get("channel").asText());
        qmgrProperties.put(CMQC.USER_ID_PROPERTY, connParams.get("user_id").asText());
        qmgrProperties.put(CMQC.PASSWORD_PROPERTY, connParams.get("password").asText());
    }

    public static void processMessage(MQMessage mqMessage) {
        String messagePayload = getMessagePayload(mqMessage);
        LocalDateTime messageTimeStamp = mqMessage.putDateTime.toZonedDateTime().toLocalDateTime().plusHours(1);
        boolean displayMessage = false;
        boolean searchParamsFilled = (!searchParameterList.isEmpty());
        boolean timeStampFilled = (!Objects.isNull(searchDateTimeFrom) && !Objects.isNull(searchDateTimeTo));
        String fileName;
        List<String> fileNameParam = new ArrayList<>();

        boolean payloadIsJSON = false;
        boolean payloadIsXML = false;
        boolean payloadIsRaw = false;

        try {
            String parseTest = prettyPrintXML(messagePayload);
            payloadIsXML = true;
        } catch (RuntimeException ignored) {}

        try {
            String parseTest = messagePayload.substring(messagePayload.indexOf("{"));
            parseTest = prettyPrintJSON(parseTest);
            payloadIsJSON = true;
        } catch (RuntimeException ignored) {}

        if (!payloadIsXML && !payloadIsJSON) {
            payloadIsRaw = true;
        }

        if (!searchParamsFilled && !timeStampFilled) {
            displayMessage = true;
        }

        if (searchParamsFilled && !timeStampFilled) {
            for (String searchParameter : searchParameterList) {
                if (messagePayload.toLowerCase().contains(searchParameter.toLowerCase())) {
                    fileNameParam.add(searchParameter);
                    displayMessage = true;
                }
            }
        }

        if (!searchParamsFilled && timeStampFilled) {
            if (messageTimeStamp.isAfter(searchDateTimeFrom) && messageTimeStamp.isBefore(searchDateTimeTo)) {
                displayMessage = true;
            }
        }

        if (searchParamsFilled && timeStampFilled) {
            if (messageTimeStamp.isAfter(searchDateTimeFrom) && messageTimeStamp.isBefore(searchDateTimeTo)) {
                for (String searchParameter : searchParameterList) {
                    if (messagePayload.toLowerCase().contains(searchParameter.toLowerCase())) {
                        fileNameParam.add(searchParameter);
                        displayMessage = true;
                    }
                }
            }
        }

        if (displayMessage) {
            System.out.print("\n| #" + messageCounter + " | " + queueName + " | " + messageTimeStamp + " |");

            if (payloadIsJSON) {
                messagePayload = messagePayload.substring(messagePayload.indexOf("{"));
                System.out.print("\n" + prettyPrintJSON(messagePayload) + "\n");
            }

            if (payloadIsXML) {
                System.out.print("\n" + prettyPrintXML(messagePayload));
            }

            if (payloadIsRaw) {
                System.out.print("\n" + messagePayload + "\n");
            }

            if (writeMessageToFile) {
                File dir = new File(workingDir);
                boolean isDirCreated = dir.mkdir();
                DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmssSS");
                String fileTimeStamp = dateTimeFormatter.format(messageTimeStamp);

                if (!fileNameParam.isEmpty()) {
                    fileName = queueName + "_" + fileTimeStamp + "_" + fileNameParam.getFirst();
                } else {
                    fileName = queueName + "_" + fileTimeStamp;
                }

                if (payloadIsXML) {
                    String filePath = workingDir + File.separator + fileName + XMLFileExtension;
                    writeFileToFS(filePath, prettyPrintXML(messagePayload));
                }

                if (payloadIsJSON) {
                    String filePath = workingDir + File.separator + fileName + JSONFileExtension;
                    writeFileToFS(filePath, prettyPrintJSON(messagePayload));
                }

                if (payloadIsRaw) {
                    String filePath = workingDir + File.separator + fileName + rawFileExtension;
                    writeFileToFS(filePath, messagePayload);
                }
            }
            messageCounter++;
            foundMessageCounter++;
            foundInQueue = true;
        }
        String statNodeKey = queueName + " (" + queueManagerName + ")";
        if (statNode.has(statNodeKey)) {
            if (displayMessage) {
                int numberOfMessages = statNode.get(statNodeKey).asInt();
                statNode.put(statNodeKey, numberOfMessages + 1);
            }
        } else {
            if (displayMessage) {
                statNode.put(statNodeKey, 1);
            } else {
                statNode.put(statNodeKey, 0);
            }
        }
    }

    public static String getUserInput() {
        String input = "";
        Scanner scanner = new Scanner(System.in);
        while (true) {
            input = scanner.nextLine();
            if (input != null) {
                break;
            }
        }
        return input;
    }

    public static void about() {
        System.out.print("\nMQSearch " + release);
        System.out.print("\nUsing IBM MQ classes for Java libraries " + clientLib);
        System.out.print("\nSource: " + about);
    }

    public static void handleMQException(MQException e) {
        browseError = true;
        System.out.print("\nError: " + e.reasonCode + " ");
        switch (e.reasonCode) {
            case 2033:
                System.out.print("MQRC_NO_MSG_AVAILABLE. Queue empty?\n");
                break;
            case 2035:
                System.out.print("MQRC_NOT_AUTHORIZED. User not authorized on queuemanager or source IP blocked?\n");
                break;
            case 2085:
                System.out.println("MQRC_UNKNOWN_OBJECT_NAME. Queue not found on queuemanager?\n");
                break;
            case 2538:
                System.out.print("MQRC_HOST_NOT_AVAILABLE. Queuemanager not reachable or bad configuration?\n");
                break;
            default:
                System.out.print("");
                break;
        }
    }

    public static String prettyPrintXML(String unformattedXML) {
        try {
            OutputFormat outputFormat = OutputFormat.createPrettyPrint();
            outputFormat.setNewLineAfterDeclaration(false);
            org.dom4j.Document document = DocumentHelper.parseText(unformattedXML);
            StringWriter stringWriter = new StringWriter();
            XMLWriter xmlWriter = new XMLWriter(stringWriter, outputFormat);
            xmlWriter.write(document);
            return stringWriter.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static String prettyPrintJSON(String unformattedJSON) {
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            return objectMapper.readTree(unformattedJSON).toPrettyString();
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
            System.out.print("\n---> Saved to " + filePath + "\n");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static String readStringFromResource(String resourceName) {
        try {
            return new String(Thread.currentThread().getContextClassLoader().getResourceAsStream(resourceName).readAllBytes());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static JsonNode readJSONFromResource(String resourceName) {
        ObjectMapper objectMapper = new ObjectMapper();
        InputStream inputStream = Thread.currentThread().getContextClassLoader().getResourceAsStream(resourceName);
        try {
            return objectMapper.readTree(inputStream);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}