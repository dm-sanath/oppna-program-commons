package se.vgregion.messagebus;

import com.liferay.portal.kernel.messaging.Message;
import com.liferay.portal.kernel.messaging.MessageBus;
import com.liferay.portal.kernel.messaging.MessageBusException;
import com.liferay.portal.kernel.messaging.MessageListener;
import com.liferay.portal.kernel.messaging.sender.DefaultSynchronousMessageSender;
import com.liferay.portal.kernel.uuid.PortalUUID;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mortbay.jetty.Server;
import org.mortbay.jetty.handler.AbstractHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.AbstractJUnit4SpringContextTests;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.Assert.assertEquals;

/**
 * User: pabe
 * Date: 2011-05-10
 * Time: 16:12
 */
@ContextConfiguration(
        locations = {"/META-INF/camelRestComponentTest.xml"})
public class CamelRestComponentTest extends AbstractJUnit4SpringContextTests {

    @Autowired
    private MessageBus messageBus;

    @Value("${messagebus.rest.destination}")
    String messagebusDestination;
    @Value("${rest.destination}")
    String restDestination;

    private Server server = new Server(8008);

    private StringBuilder expected;

    @Before
    public void setUp() throws Exception {
        server.addHandler(new AbstractHandler() {
            @Override
            public void handle(String s, HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse, int i) throws IOException, ServletException {
                expected = new StringBuilder();
                System.out.println(httpServletRequest);
                PrintWriter writer = httpServletResponse.getWriter();
                writer.append("testsvar");
                expected.append("testsvar");
                Random r = new Random();
                for (int j = 0; j < 10; j++) {
                    int value = r.nextInt(Integer.MAX_VALUE);
                    writer.append(value + "");
                    expected.append(value);
                }
                System.out.println();
                writer.close();
                httpServletResponse.setStatus(HttpServletResponse.SC_OK);
            }
        });
        server.start();

    }

    @After
    public void tearDown() throws Exception {
//        server.stop();
    }

    @Test
    @DirtiesContext
    public void test() throws MessageBusException {
        final List<Object> list = new ArrayList();

        String destinationName = messagebusDestination;

        Message message = new Message();
        message.setPayload("test");

/*
        messageBus.registerMessageListener("vgr/messagebus_rest_test_destination.REPLY", new MessageListener() {
            @Override
            public void receive(Message message) {
                assertEquals("test", message.getPayload());
                list.add(new Object());
            }
        });
*/


        DefaultSynchronousMessageSender sender = new DefaultSynchronousMessageSender();
        sender.setPortalUUID(new PortalUUID() {
            @Override
            public String generate() {
                Random random = new Random();
                return random.nextInt() + "";
            }
        });
        sender.setMessageBus(messageBus);
        Object result = sender.send(destinationName, message);
        assertEquals(expected.toString(), result);
    }
}