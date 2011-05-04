package se.vgregion.routes;

import org.apache.camel.spring.SpringRouteBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * User: pabe
 * Date: 2011-04-27
 * Time: 10:29
 */
public class MessagebusJmsRouteBuilder extends SpringRouteBuilder {
    private static Logger log = LoggerFactory.getLogger(MessagebusJmsRouteBuilder.class);

    private String messageBusDestination;
    private String activeMqDestination;

    public MessagebusJmsRouteBuilder(String messageBusDestination, String activeMqDestination) {
        this.messageBusDestination = messageBusDestination;
        this.activeMqDestination = activeMqDestination;

        log.info("MB: {} MQ: {}", messageBusDestination, activeMqDestination);
    }

    @Override
    public void configure() throws Exception {
        from("liferay:" + messageBusDestination)
                .log("1 b: ${body} h: ${header.JMSCorrelationID}")
                .to("activemq:queue:" + activeMqDestination + "?preserveMessageQos=true&replyTo=" + activeMqDestination + ".REPLY");

        from("activemq:queue:" + activeMqDestination + ".REPLY?disableReplyTo=true")
                .log("3 b: ${body} h: ${headers}")
                .to("liferay:" + messageBusDestination + ".REPLY");
    }
}
