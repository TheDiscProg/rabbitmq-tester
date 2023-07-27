# dapex-rabbitmq tester
This is a self-contained Docker service to test the RabbitMQ library, i.e. the client and consumer.
The docker-compose YML will load a local RabbitMQ in the image and you should be able to run the MainApp program.

It runs for 50 seconds and publishes in parallel to three RabbitMQ queues.

