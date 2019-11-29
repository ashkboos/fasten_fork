package eu.fasten.core.plugins;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

/**
 * Currently, this is a dummy analyzer, which shows Maven coordinates that were produced.
 */
public class Analyzer implements KafkaConsumer<String>{

    private final Logger logger = LoggerFactory.getLogger(Analyzer.class.getName());

    @Override
    public String name() {
        return "DummyAnalyzer";
    }

    @Override
    public String description() {
        return "DummyAnalyzer";
    }

    @Override
    public void start() {

    }

    @Override
    public void stop() {

    }

    @Override
    public String consumerTopic() {
        return "maven.packages";
    }

    @Override
    public void consume(ConsumerRecords<String, String> records) {
        for(ConsumerRecord<String, String> record : records){

            //TODO: Call graph generator should take Maven coordinates here.

            logger.info("Key: " + record.key() + " , Value: " + record.value());
            logger.info("Partition: " + record.partition() + ", Offset: " + record.offset());
        }

    }
}