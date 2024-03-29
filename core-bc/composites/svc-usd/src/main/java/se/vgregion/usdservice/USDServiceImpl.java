/**
 * Copyright 2010 Västra Götalandsregionen
 *
 *   This library is free software; you can redistribute it and/or modify
 *   it under the terms of version 2.1 of the GNU Lesser General Public
 *   License as published by the Free Software Foundation.
 *
 *   This library is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU Lesser General Public License for more details.
 *
 *   You should have received a copy of the GNU Lesser General Public
 *   License along with this library; if not, write to the
 *   Free Software Foundation, Inc., 59 Temple Place, Suite 330,
 *   Boston, MA 02111-1307  USA
 *
 */

package se.vgregion.usdservice;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.rmi.RemoteException;
import java.util.*;

import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.mail.util.ByteArrayDataSource;
import javax.xml.namespace.QName;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.ws.Holder;
import javax.xml.xpath.*;

import com.ca.www.UnicenterServicePlus.ServiceDesk.*;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import org.xml.sax.SAXException;
import se.vgregion.usdservice.domain.Issue;
import se.vgregion.util.Attachment;

/**
 * @author David Rosell - Redpill-Linpro
 */
public class USDServiceImpl implements USDService {

    private URL endPoint;
    private String wsUser;
    private String wsPassword;
    private String wsAttachmentRepHandle;
    private USDWebServiceSoap ws;
    private static final Log log = LogFactory.getLog(USDServiceImpl.class);

    private Properties usdAppToGroupMappings;

    // Define which attributes to fetch when retrieving contact's issue list
    private static final List<String> ATTRIBUTE_NAMES = Arrays.asList("description", "summary", "status.sym",
            "ref_num", "web_url", "type");
    private static final List<String> ATTRIBUTE_NAMES_CHG = Arrays.asList("description", "summary", "status.sym",
            "chg_ref_num", "web_url");

    private static final String TYPE_CHANGE_ORDER = "C";


    public USDServiceImpl(Properties p) {
        String sEndPoint = p.getProperty("endpoint");
        try {
            this.endPoint = new URL(sEndPoint);
        } catch (MalformedURLException e) {
            throw new RuntimeException("URL not found:" + sEndPoint, e);
        }
        this.wsUser = p.getProperty("user");
        this.wsPassword = p.getProperty("password");
        this.wsAttachmentRepHandle = p.getProperty("repositoryHandle");
    }

    public Properties getUsdAppToGroupMappings() {
        return usdAppToGroupMappings;
    }

    public void setUsdAppToGroupMappings(Properties appToGroupMappings) {
        this.usdAppToGroupMappings = appToGroupMappings;
    }

    /*
     * (non-Javadoc)
     *
     * @see se.vgregion.usdservice.USDService#lookupIssues(java.lang.String, java.lang.Integer)
     */
    @Override
    public List<Issue> lookupIssues(String userId, int maxRows, boolean includeGroups) {
        int sessionID = 0;
        try {
            sessionID = getUSDWebService().login(wsUser, wsPassword);

            String contactHandle = lookupContactHandle(userId, sessionID);
            if (contactHandle == null) {
                // User not registered in USD, return null
                return null;
            }

            List<Issue> records = new ArrayList<Issue>();
            // Change Orders - Assignee
            records.addAll(getChangeOrdersForAssignee(sessionID, contactHandle, maxRows));
            // Incidents, Problems, Requests - Assignee
            records.addAll(getRequestForAssignee(sessionID, contactHandle, maxRows));

            // Change Orders - Affected
            records.addAll(getChangeOrdersForContact(sessionID, contactHandle, maxRows));
            // Incidents, Problems, Requests - Affected
            records.addAll(getRequestForContact(sessionID, contactHandle, maxRows));

            // Group issues - not assigen to user
            if (includeGroups) {
                String groups = lookupUserGroups(sessionID, contactHandle);
                if (groups.length() > 0) {
                    records.addAll(getChangeOrdersForContactByGroup(sessionID, contactHandle, groups, maxRows));
                    records.addAll(getRequestForContactByGroup(sessionID, contactHandle, groups, maxRows));
                }
            }

            List<Issue> listWithoutDuplicates = new ArrayList<Issue>();
            for (Issue record : records) {
                if (!listWithoutDuplicates.contains(record)) {
                    listWithoutDuplicates.add(record);
                }
            }

            return listWithoutDuplicates;
        } catch (RemoteException e) {
            log.error("Failed to get issue list from USD Service for user=" + userId, e);
            throw new RuntimeException(e);
        } finally {
            getUSDWebService().logout(sessionID);
        }
    }


    /* TODO: Cannot upload attachments */
    @Override
    public String createRequest(Properties requestParameters, String userId, Collection<Attachment> attachments) {
        int sessionID = 0;
        try {
            sessionID = getUSDWebService().login(wsUser, wsPassword);

            String contactHandle = lookupContactHandle(userId, sessionID);
            if (contactHandle == null) {
                // Use the wsUser as fallback if the user is unknown
                contactHandle = lookupContactHandle(wsUser, sessionID);
            }

            requestParameters.setProperty("customer", contactHandle);

            List<String> lAttributes = new ArrayList<String>();
            List<String> lAttributeValues = new ArrayList<String>();

            for (Enumeration<Object> e = requestParameters.keys(); e.hasMoreElements(); ) {
                String key = (String) e.nextElement();
                lAttributes.add(key);
                lAttributeValues.add(key);
                lAttributeValues.add(requestParameters.getProperty(key));
            }

            List<String> properties = Collections.<String>emptyList();

            Holder<String> reqHandle = new Holder<String>("");
            Holder<String> reqNumber = new Holder<String>("");

            String template = "";

            Holder<String> result = new Holder<String>();
            getUSDWebService().createRequest(sessionID, contactHandle,
                    toArrayOfString(lAttributeValues),
                    toArrayOfString(properties),
                    template, toArrayOfString(lAttributes), reqHandle, reqNumber, result);

            String handle = null;
            try {
                handle = extractHandle(result.toString());
            } catch (Exception e) {
                throw new RuntimeException("Error parsing handle to USD incident from xml response...\n" + result, e);
            }

            if (!StringUtils.isBlank(handle)) {
                for (Attachment attachment : attachments) {
                    int i = 0;
                    try {
                        createAttachment(sessionID, wsAttachmentRepHandle, handle, "Attachment " + i, attachment);
                    } catch (Exception e) {
                        log.error("Failed to create attachment in USD [" + attachment.getFilename() + "]");
                    }
                    i++;
                }

            }

            return result.toString();
        } finally {
            getUSDWebService().logout(sessionID);
        }
    }

    /**
     * BOPSID is a temporary single signon id.
     * <p/>
     * This has to be used rather quickly before it is invalidated.
     *
     * @param userId, the login name of the user to be signed in.
     * @return BOPSID.
     */
    @Override
    public String getBopsId(String userId) {
        int sessionID = 0;
        try {
            sessionID = getUSDWebService().login(wsUser, wsPassword);

            return getUSDWebService().getBopsid(sessionID, userId);
        } finally {
            getUSDWebService().logout(sessionID);
        }
    }

    @Override
    public String getUSDGroupHandleForApplicationName(String appName) {
        String usdGroupName = usdAppToGroupMappings.getProperty(appName);

        String result = getGroupHandle(usdGroupName);
        if (result == null || result.length() == 0) {
            throw new RuntimeException("No group handle found for application name=" + appName);
        }
        return result;
    }

    /**
     * USD-WS lookup.
     */
    private List<Issue> getRequestForContact(int sessionID, String contactHandle, int maxRows)
            throws RemoteException {
        // Build where clause
        String whereClause = String.format("customer = U'%1$s' AND assignee <> U'%1$s' AND active = 1", contactHandle);

        // Get list xml
        String listXml = getUSDWebService().doSelect(sessionID, "cr", whereClause, maxRows,
                toArrayOfString(ATTRIBUTE_NAMES));

        // Parse xml to list
        return parseIssues(listXml, null, "U");
    }

    /**
     * USD-WS lookup.
     */
    private List<Issue> getRequestForAssignee(int sessionID, String contactHandle, int maxRows)
            throws RemoteException {
        // Build where clause
        String whereClause = String.format("assignee = U'%s' AND active = 1", contactHandle);

        // Get list xml
        String listXml = getUSDWebService().doSelect(sessionID, "cr", whereClause, maxRows,
                toArrayOfString(ATTRIBUTE_NAMES));

        // Parse xml to list
        return parseIssues(listXml, null, "A");
    }

    /**
     * USD-WS lookup.
     */
    private List<Issue> getRequestForContactByGroup(int sessionID, String contactHandle, String groups, int maxRows)
            throws RemoteException {

        // Build where clause
        String whereClause = String.format("group in (%1$s) AND customer <> U'%2$s' AND assignee <> U'%2$s'" +
                " AND active = 1", groups, contactHandle);

        // Get list xml
        String listXml = getUSDWebService().doSelect(sessionID, "cr", whereClause, maxRows,
                toArrayOfString(ATTRIBUTE_NAMES));

        // Parse xml to list
        return parseIssues(listXml, null, "G");
    }

    /**
     * USD-WS lookup.
     */
    private List<Issue> getChangeOrdersForContact(int sessionID, String contactHandle, int maxRows)
            throws RemoteException {
        // Build where clause
        String whereClause = String.format("affected_contact = U'%1$s' AND active = 1",
                contactHandle);

        // Get list xml
        String listXml = getUSDWebService().doSelect(sessionID, "chg", whereClause, maxRows,
                toArrayOfString(ATTRIBUTE_NAMES_CHG));

        // Parse xml to list
        return parseIssues(listXml, TYPE_CHANGE_ORDER, "U");
    }

    private List<Issue> getChangeOrdersForAssignee(int sessionID, String contactHandle, int maxRows)
            throws RemoteException {
        // Build where clause
        String whereClause = String.format("assignee = U'%s' AND active = 1", contactHandle);

        // Get list xml
        String listXml = getUSDWebService().doSelect(sessionID, "chg", whereClause, maxRows,
                toArrayOfString(ATTRIBUTE_NAMES_CHG));

        // Parse xml to list
        return parseIssues(listXml, TYPE_CHANGE_ORDER, "A");
    }

    /**
     * USD-WS lookup.
     */
    private List<Issue> getChangeOrdersForContactByGroup(int sessionID, String contactHandle, String groups, int maxRows)
            throws RemoteException {

        // Build where clause
        String whereClause = String.format("group in (%1$s) AND affected_contact <> U'%2$s' AND assignee <> U'%2$s'" +
                " AND active = 1", groups, contactHandle);

        // Get list xml
        String listXml = getUSDWebService().doSelect(sessionID, "chg", whereClause, maxRows,
                toArrayOfString(ATTRIBUTE_NAMES_CHG));

        // Parse xml to list
        return parseIssues(listXml, TYPE_CHANGE_ORDER, "G");
    }

    /**
     * Lookup the groups the user is member of.
     *
     * @param sessionID     - USD session id.
     * @param contactHandle - handle to the user.
     * @return - a comma separated string of group handles.
     */
    private String lookupUserGroups(int sessionID, String contactHandle) {
        // Build where clause
        String whereClause = String.format("member = U'%s'", contactHandle);

        // Get list xml
        String listXml = getUSDWebService().doSelect(sessionID, "grpmem", whereClause, -1,
                toArrayOfString(Arrays.asList("group")));

        return parseUserGroups(listXml);
    }

    /**
     * Parse group id's from from a USD-query.
     *
     * @param xml - standard USD query response.
     * @return - a formated (comma separated) string of group handles.
     */
    private String parseUserGroups(String xml) {
        String groups = "";
        try {
            // Parse the XML to get a DOM to query
            Document doc = parseXml(xml);

            // Extract USDObject's
            String xPath = "/UDSObjectList/UDSObject";
            NodeList udsObjects = evaluate(xPath, doc, XPathConstants.NODESET);

            // Iterate over USDObject's to create Issue's
            for (int i = 1; i < udsObjects.getLength() + 1; i++) {
                if (groups.length() > 0) groups += ",";
                groups += "U'" + extractAttribute(i, "group", XPathConstants.STRING, doc) + "'";
            }

            return groups;
        } catch (Exception e) {
            String msg = "Error when parsing group handles from XML";
            log.error(msg);
            throw new RuntimeException(msg, e);
        }
    }

    protected static List<Issue> parseIssues(String xml, String fallbackType, String associated) throws RuntimeException {
        List<Issue> issueList = new ArrayList<Issue>();
        try {
            // Parse the XML to get a DOM to query
            Document doc = parseXml(xml);

            // Extract USDObject's
            String xPath = "/UDSObjectList/UDSObject";
            NodeList udsObjects = evaluate(xPath, doc, XPathConstants.NODESET);

            // Iterate over USDObject's to create Issue's
            for (int i = 1; i < udsObjects.getLength() + 1; i++) {
                // Get ref_num
                String refNum = null;
                if (TYPE_CHANGE_ORDER.equals(fallbackType)) {
                    refNum = extractAttribute(i, "chg_ref_num", XPathConstants.STRING, doc);
                } else {
                    refNum = extractAttribute(i, "ref_num", XPathConstants.STRING, doc);
                }

                // A Issue has to have refNum to be valid
                if (StringUtils.isBlank(refNum)) {
                    continue;
                }

                Issue issue = resolveIssue(refNum, i, fallbackType, associated, doc);

                // Add Issue object to list
                issueList.add(issue);
            }

            return issueList;
        } catch (Exception e) {
            log.error("Error when trying to parse issue list from XML", e);
            throw new RuntimeException("Error when trying to parse issue list from XML", e);
        }
    }

    private static Issue resolveIssue(String refNum, int i, String fallbackType, String associated, Document doc)
            throws XPathExpressionException {
        Issue issue = new Issue();
        issue.setRefNum(refNum);

        // Get summary
        String summary = extractAttribute(i, "summary", XPathConstants.STRING, doc);
        issue.setSummary(summary);

        // Get description
        String description = extractAttribute(i, "description", XPathConstants.STRING, doc);
        issue.setDescription(description);

        // Get status
        String statusSym = extractAttribute(i, "status.sym", XPathConstants.STRING, doc);
        issue.setStatus(statusSym);

        // Get web_url
        String webUrl = extractAttribute(i, "web_url", XPathConstants.STRING, doc);
        webUrl = webUrl.replaceFirst("vgms0005", "vgrusd.vgregion.se");
        issue.setUrl(webUrl);

        // Get type
        String type = extractAttribute(i, "type", XPathConstants.STRING, doc);
        if (StringUtils.isBlank(type)) {
            type = fallbackType;
        }
        issue.setType(type);

        issue.setAssociated(associated);

        return issue;
    }

    private static String extractAttribute(int cnt, String attrName, QName attrType, Document source)
            throws XPathExpressionException {
        String exprTemplate = "/UDSObjectList/UDSObject[%s]/Attributes/Attribute[AttrName='%s']/AttrValue";
        String xPath = String.format(exprTemplate, cnt, attrName);

        return evaluate(xPath, source, XPathConstants.STRING);
    }

    /**
     * USD-WS lookup.
     */
    private String lookupContactHandle(String userId, int sessionID) {
        String contactHandle = null;
        try {
            contactHandle = getUSDWebService().getHandleForUserid(sessionID, userId);
            // Rid object type from handle
            contactHandle = contactHandle.replaceFirst("cnt:", "");
        } catch (Throwable e) {
            log.error("Could not get handle for user with userId " + userId);
        }
        return contactHandle;
    }

    /**
     * USD-WS lookup.
     */
    private String getGroupHandle(String groupName) {

        int sessionID = 0;
        String whereClause = String.format("type = 2308 and delete_flag = 0 and last_name = '%s'", groupName);

        try {
            sessionID = getUSDWebService().login(wsUser, wsPassword);
            String resultXml = getUSDWebService().doSelect(sessionID, "cnt", whereClause, -1,
                    toArrayOfString(Arrays.<String>asList("last_name")));

            return extractHandle(resultXml);
        } catch (RemoteException e) {
            throw new RuntimeException("Error when getting group handle", e);
        } catch (Exception e) {
            throw new RuntimeException("Error when parsing xml response when searching for a group", e);
        }

    }

    /**
     * USD-WS lookup.
     */
    private void createAttachment(int sid, String repHandle, String objectHandle, String description,
            Attachment attachment) throws Exception {

        DataHandler dhandler = createDataHandler(attachment);

        // Affix DIME type header to attachment before sending
//        ((javax.xml.rpc.Stub) getWebService())._setProperty(org.apache.axis.client.Call.ATTACHMENT_ENCAPSULATION_FORMAT,
//                Call.ATTACHMENT_ENCAPSULATION_FORMAT_DIME);
//        ((org.apache.axis.client.Stub) getWebService()).addAttachment(dhandler);
        // Create attachment
//        getWebService().createAttachment(sid, repHandle, objectHandle, description, attachment.getFilename());
        getUSDWebService().createAttachment(sid, repHandle, objectHandle, description, attachment.getFilename());

    }

    private DataHandler createDataHandler(Attachment attachment) {
        DataSource source;
        try {
            source = new ByteArrayDataSource(attachment.getData(), "application/octet-stream");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return new DataHandler(source);
    }

    protected String extractHandle(String xml) throws Exception {
        Document doc = parseXml(xml);

        NodeList handles = doc.getElementsByTagName("Handle");
        if (handles.getLength() > 0) {
            return handles.item(0).getFirstChild().getNodeValue();
        }
        return "";
    }

    /**
     * Convenience method.
     *
     * @param xml, the xml to be parsed
     * @return a DOM Document
     * @throws ParserConfigurationException
     * @throws SAXException
     * @throws IOException
     */
    private static Document parseXml(String xml) throws ParserConfigurationException, SAXException, IOException {
        DocumentBuilderFactory dbfactory = DocumentBuilderFactory.newInstance();
        dbfactory.setNamespaceAware(true);
        dbfactory.setXIncludeAware(true);

        DocumentBuilder parser = dbfactory.newDocumentBuilder();

        // To avoid illegal xml character references
        xml = xml.replace("&", "&amp;");
        ByteArrayInputStream bais = new ByteArrayInputStream(xml.getBytes("UTF-8"));
        return parser.parse(bais);
    }

    /**
     * Convenience method.
     * <p/>
     * Neither XPathFactory nor XPath are thread safe. Further more XPath is not re-entrant,
     * so a new instance has to be created for every evaluation.
     * The generic marker make this more convenient to use - however QName has to match the expected return type.
     *
     * @param xPath,  the xPath to be evaluated.
     * @param source, the source document to evaluate on.
     * @param qName,  the node type the evaluation returns.
     * @param <T>,    matching return type.
     * @return the evaluated result.
     * @throws XPathExpressionException
     */
    private static <T> T evaluate(String xPath, Document source, QName qName) throws XPathExpressionException {
        XPathFactory factory = XPathFactory.newInstance();
        XPath processor = factory.newXPath();
        return (T) processor.evaluate(xPath, source, qName);
    }

    private ArrayOfString toArrayOfString(List<String> source) {
        ArrayOfString target = new ArrayOfString();
        for (String val : source) {
            target.getString().add(val);
        }
        return target;
    }

    private USDWebServiceSoap getUSDWebService() {
        if (ws == null) {
            QName qName = new QName("http://www.ca.com/UnicenterServicePlus/ServiceDesk", "USD_WebService");

            USDWebService localUsdWs = null;
            try {
                localUsdWs = new USDWebService(endPoint, qName);
            } catch (Exception ex) {
                String endPointMessage = (endPoint != null) ? endPoint.toString() : "EndPoint not configured";
                log.error("Failed when trying to connect to USDWebService [" + endPointMessage + "]");
            }

            ws = new USDWebService(endPoint, qName).getUSDWebServiceSoap();
        }
        return ws;
    }
}
