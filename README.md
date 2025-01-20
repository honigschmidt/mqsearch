Java console client for IBM MQ to browse, search and save queue content
+ Select queues manually or load queue list from file
+ Search for messages by adding search parameters and timestamp
+ Pretty print for JSON and XML messages
+ Supports multiple environments

Please check the included screenshot for details.

Usage:
1. Create a directory under /resources with the environment name. DEV is already created.
2. Make a "qmgr_config.json" file and fill it with the connection parameters of the queue managers. See example included in the /DEV directory.
3. Run "CreateQConfig.java" with environment name as argument to connect to the specified queue managers and get the list of queues. The queue list "q_config.json" will be saved into your HOME directory. Copy this file to the environment directory. You can also manually create the file, see example in the /DEV directory.
4. Run "MQSearch.java"

You can test the application with the IBM MQ container provided by IBM. More on that under the [link](https://developer.ibm.com/tutorials/mq-connect-app-queue-manager-containers/). The application is preconfigured to work with the container.