package se.vgregion.routes;

import com.liferay.portal.kernel.messaging.DestinationNames;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.spring.SpringRouteBuilder;
import se.vgregion.authentication.AuthParams;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Class for building camel routes with HTTP.
 * <p/>
 * User: pabe
 * Date: 2011-05-10
 * Time: 15:56
 */
public class MessagebusHttpRouteBuilder extends SpringRouteBuilder {

    private String messageBusDestination;
    private String httpDestination;
    private AuthParams authParams;

    /**
     * Constructor.
     *
     * @param messageBusDestination messageBusDestination
     * @param httpDestination       restDestination
     * @param authParams            authParams
     */
    public MessagebusHttpRouteBuilder(String messageBusDestination, String httpDestination,
                                      AuthParams authParams) {
        this.messageBusDestination = messageBusDestination;
        this.httpDestination = httpDestination;
        this.authParams = authParams;

        log.info("MB: {} HTTP: {}", messageBusDestination, httpDestination);
    }

    /**
     * Constructor.
     *
     * @param messageBusDestination messageBusDestination
     * @param httpDestination       restDestination
     */
    public MessagebusHttpRouteBuilder(String messageBusDestination, String httpDestination) {
        this(messageBusDestination, httpDestination, null);
    }

    @Override
    public void configure() throws Exception {
        String extraOptionsString = "";
        if (authParams != null) {
            extraOptionsString = "&authUsername=" + authParams.getUsername() + "&authPassword="
                    + authParams.getPassword() + "&authMethod=" + authParams.getMethod();
        }

        from("liferay:" + messageBusDestination)
                .errorHandler(deadLetterChannel("direct:error_" + messageBusDestination))
                .setProperty("correlationId", header("responseId"))
                //if the body of the message is not null Camel will do a POST request
                .inOut(httpDestination + "?httpClientConfigurer=myConfigurer" + extraOptionsString)// + "&proxyHost=localhost&proxyPort=8888")
                .process(new Processor() {
                    @Override
                    public void process(Exchange exchange) throws Exception {
                        String result = extractResponseBody(exchange);
                        exchange.getOut().setBody(result);
                    }
                })
                .setHeader("responseId", property("correlationId"))
                .to("liferay:" + DestinationNames.MESSAGE_BUS_DEFAULT_RESPONSE);

        errorHandler();
    }

    protected void errorHandler() {
        from("direct:error_" + messageBusDestination)
                .process(new ErrorProcessor())
                .setHeader("responseId", property("correlationId"))
                .to("liferay:" + DestinationNames.MESSAGE_BUS_DEFAULT_RESPONSE);
    }

    private String extractResponseBody(Exchange exchange) throws IOException {
//        Response response = (Response) exchange.getIn().getBody();

        InputStream inputStream = null;
        BufferedInputStream bis = null;
        try {
            inputStream = (InputStream) exchange.getIn().getBody();
//            inputStream = (InputStream) (response).getEntity();
            bis = new BufferedInputStream(inputStream);
            final int i = 1024;
            byte[] buffer = new byte[i];
            int n;
            StringBuilder sb = new StringBuilder();
            while ((n = bis.read(buffer)) > 0) {
                sb.append(new String(buffer, 0, n, "UTF-8"));
            }
            return sb.toString();
        } finally {
            if (bis != null) {
                bis.close();
            }
            if (inputStream != null) {
                inputStream.close();
            }
        }
    }
}
