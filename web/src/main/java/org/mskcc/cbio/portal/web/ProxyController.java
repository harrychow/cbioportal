/*
 * Copyright (c) 2015 Memorial Sloan-Kettering Cancer Center.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY, WITHOUT EVEN THE IMPLIED WARRANTY OF MERCHANTABILITY OR FITNESS
 * FOR A PARTICULAR PURPOSE. The software and documentation provided hereunder
 * is on an "as is" basis, and Memorial Sloan-Kettering Cancer Center has no
 * obligations to provide maintenance, support, updates, enhancements or
 * modifications. In no event shall Memorial Sloan-Kettering Cancer Center be
 * liable to any party for direct, indirect, special, incidental or
 * consequential damages, including lost profits, arising out of the use of this
 * software and its documentation, even if Memorial Sloan-Kettering Cancer
 * Center has been advised of the possibility of such damage.
 */

/*
 * This file is part of cBioPortal.
 *
 * cBioPortal is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package org.mskcc.cbio.portal.web;

import org.mskcc.cbio.portal.model.virtualstudy.VirtualStudyData;
import org.json.simple.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.Map;

@Controller
@RequestMapping("/proxy")
public class ProxyController
{
	
  private String hotspotsURL;
  @Value("${hotspots.url:https://www.cancerhotspots.org/api/}")
  public void setHotspotsURL(String property) { this.hotspotsURL = property; }
  
  private String bitlyURL;
  @Value("${bitly.url}")
  public void setBitlyURL(String property) { this.bitlyURL = property; }

  private String sessionServiceURL;
  @Value("${session.service.url:''}") // default is empty string
  public void setSessionServiceURL(String property) { this.sessionServiceURL = property; }

  private String oncokbApiURL;
  @Value("${oncokb.api.url:http://oncokb.org/legacy-api/}")
  public void setOncoKBURL(String property) {
      // The annotation above can only prevent oncokb.api.url is not present in the property file.
      // If user set the  oncokb.api.url to empty, we should also use the default OncoKB URL.
      if (property.isEmpty()) {
          property = "http://oncokb.org/legacy-api/";
      }
      this.oncokbApiURL = property;
  }
    
    private Boolean enableOncokb;

    @Value("${show.oncokb:true}")
    public void setEnableOncokb(Boolean property) {
        if(property == null) {
            property = true;
        }
        this.enableOncokb = property;
    }
  // This is a general proxy for future use.
  // Please modify and improve it as needed with your best expertise. The author does not have fully understanding
  // of JAVA proxy when creating this proxy.
  // Created by Hongxin
  @RequestMapping(value="/{path}")
  public @ResponseBody String getProxyURL(@PathVariable String path,
                                          @RequestBody(required = false) String body, HttpMethod method,
                                          HttpServletRequest request, HttpServletResponse response) throws URISyntaxException, IOException
  {
      Map<String, String> pathToUrl = new HashMap<>();
      
      pathToUrl.put("bitly", bitlyURL);
      pathToUrl.put("cancerHotSpots", hotspotsURL + "hotspots/single/");
      pathToUrl.put("3dHotspots", "https://www.3dhotspots.org/api/hotspots/3d/");
      pathToUrl.put("oncokbAccess", oncokbApiURL + "access");
      pathToUrl.put("oncokbSummary", oncokbApiURL + "summary.json");

      String URL = pathToUrl.get(path) == null ? "" : pathToUrl.get(path);
        
        if(path != null && StringUtils.startsWithIgnoreCase(path, "oncokb") && !enableOncokb) {
            response.sendError(403, "OncoKB service is disabled.");
            return "";
        }
        
        //If request method is GET, include query string
        if (method.equals(HttpMethod.GET) && request.getQueryString() != null){
          URL += "?" + request.getQueryString();
        }
        return respProxy(URL, method, body, response);
  }

    @RequestMapping(value="/oncokbSummary", method = RequestMethod.POST)
    public @ResponseBody String getOncoKBSummary(@RequestBody String body, HttpMethod method,
                                                  HttpServletRequest request, HttpServletResponse response) throws URISyntaxException, IOException {
        if(!enableOncokb) {
            response.sendError(403, "OncoKB service is disabled.");
            return "";
        }
        return respProxy(oncokbApiURL + "summary.json", method, body, response);
    }

    @RequestMapping(value="/oncokbEvidence", method = RequestMethod.POST)
    public @ResponseBody String getOncoKBEvidence(@RequestBody JSONObject body, HttpMethod method,
                                          HttpServletRequest request, HttpServletResponse response) throws URISyntaxException, IOException {
        if(!enableOncokb) {
            response.sendError(403, "OncoKB service is disabled.");
            return "";
        }
        return respProxy(oncokbApiURL + "evidence.json", method, body, response);
    }

  @RequestMapping(value="/oncokb", method = RequestMethod.POST)
  public @ResponseBody String getOncoKB(@RequestBody JSONObject body, HttpMethod method,
                                          HttpServletRequest request, HttpServletResponse response) throws URISyntaxException, IOException {
      if(!enableOncokb) {
          response.sendError(403, "OncoKB service is disabled.");
          return "";
      }
      return respProxy(oncokbApiURL + "indicator.json", method, body, response);
  }
  
     private String respProxy(String url, HttpMethod method, Object body, HttpServletResponse response) throws IOException {
        try {
            RestTemplate restTemplate = new RestTemplate();
            URI uri = new URI(url);

            ResponseEntity<String> responseEntity =
                restTemplate.exchange(uri, method, new HttpEntity<>(body), String.class);

            return responseEntity.getBody();
        }catch (Exception exception) {
            String errorMessage = "Unexpected error: " + exception.getLocalizedMessage();
            response.sendError(503, errorMessage);
            return errorMessage;
        }
     }

  @RequestMapping(value="/bitly", method = RequestMethod.GET)
  public @ResponseBody String getBitlyURL(HttpMethod method, HttpServletRequest request, HttpServletResponse response) throws URISyntaxException, IOException
  {
      return respProxy(bitlyURL + request.getQueryString(), method, null, response);
  }

    @RequestMapping(value = "/session-service/{type}/{key}", method = RequestMethod.GET)
    public
    @ResponseBody
    String getSessionService(@PathVariable String type, @PathVariable String key,
                             HttpMethod method,HttpServletRequest request, HttpServletResponse response) throws URISyntaxException, IOException {
        return respProxy(sessionServiceURL + type + "/" + key, method, null, response);
    }
    
  @RequestMapping(value="/session-service/{type}", method = RequestMethod.POST)
  public @ResponseBody Map addSessionService(@PathVariable String type, @RequestBody JSONObject body, HttpMethod method,
                                                HttpServletRequest request, HttpServletResponse response) throws IOException
  {
	  HttpEntity httpEntity = new HttpEntity<JSONObject>(body);
	  if(type.equals("virtual_study")) {
			ObjectMapper mapper = new ObjectMapper();
			  //JSON from file to Object
			VirtualStudyData virtualStudyData = mapper.readValue(body.toString(), VirtualStudyData.class);
			  Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
			  if (authentication != null && !(authentication instanceof AnonymousAuthenticationToken)) {
				  virtualStudyData.setOwner(authentication.getName());
				  httpEntity = new HttpEntity<VirtualStudyData>(virtualStudyData);
			  }
	  }
	    // returns {"id":"5799648eef86c0e807a2e965"}
	    // using HashMap because converter is MappingJackson2HttpMessageConverter (Jackson 2 is on classpath)
	    // was String when default converter StringHttpMessageConverter was used
    	  	RestTemplate restTemplate = new RestTemplate();
    	  	
    	    ResponseEntity<HashMap> responseEntity =
    	    	      restTemplate
    	    	      		  .exchange(sessionServiceURL + type, 
    	    	      					method, 
    	    	      					new HttpEntity<JSONObject>(body), 
    	    	      					HashMap.class);

    	    	return responseEntity.getBody();
    	    	
  }
}
