package se.vgr.usdservice;

import java.io.File;
import java.util.List;
import java.util.Properties;

import se.vgr.usdservice.domain.Issue;

/**
 * 
 * @author Andrew Culbert
 * @author Ulf Carlsson
 * 
 */
public interface USDService {

    String createRequest(Properties testParameters, String string, List<File> files);

    String getUSDGroupHandleForApplicationName(String appName);

    List<Issue> getIssuesForContact(String userId, Integer maxRows);

}