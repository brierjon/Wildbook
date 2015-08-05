package org.ecocean.rest;

import org.ecocean.Shepherd;
import org.ecocean.User;
import org.ecocean.servlet.ServletUtilities;

import javax.validation.Valid;
import javax.jdo.*;
import javax.servlet.http.HttpServletRequest;

import java.util.HashMap;
import org.ecocean.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;

import org.ecocean.rest.SimpleFactory;
//import org.ecocean.rest.SimpleUser;
import org.ecocean.security.Stormpath;
import com.stormpath.sdk.account.AccountList;
import com.stormpath.sdk.account.Account;
//import com.stormpath.sdk.account.AccountStatus;
import com.stormpath.sdk.client.Client;
import com.stormpath.sdk.directory.CustomData;


///// TODO should this be returning a SimpleUser now instead?
@RestController
@RequestMapping(value = "/obj/user")
public class UserController {

    private static Logger log = LoggerFactory.getLogger(MediaSubmissionController.class);

    public PersistenceManager getPM(final HttpServletRequest request) {
        String context = "context0";
        context = ServletUtilities.getContext(request);
        Shepherd myShepherd = new Shepherd(context);
        return myShepherd.getPM();
    }

    @RequestMapping(value = "", method = RequestMethod.GET)
    public ResponseEntity<User> save(final HttpServletRequest request) {
        String context = "context0";
        context = ServletUtilities.getContext(request);
        Shepherd myShepherd = new Shepherd(context);
        User user = null;
        String username = null;
        if (request.getUserPrincipal() != null) username = request.getUserPrincipal().getName();
        if ((username != null) && !username.equals("")) user = myShepherd.getUser(username);
        if (user == null) {
            user = new User();
        }
        return new ResponseEntity<User>(user, HttpStatus.OK);
    }

    @RequestMapping(value = "verify", method = RequestMethod.POST)
    public HashMap<String,Object> verifyEmail(final HttpServletRequest request,
                                              @RequestBody @Valid String email) {
System.out.println("email -> (" + email + ")");
        Client client = Stormpath.getClient(ServletUtilities.getConfigDir(request));
        HashMap<String,Object> rtn = new HashMap<String,Object>();
        rtn.put("success", false);  //assuming rtn will only be used on errors -- user is returned upon success
        if (client == null) {
            rtn.put("error", "Could not initiate Stormpath client");
            //throw new Exception();
            return rtn;
        }
        HashMap<String,Object> q = new HashMap<String,Object>();
        q.put("email", email);
        AccountList accs = Stormpath.getAccounts(client, q);
        if ((accs == null) || (accs.getSize() < 1)) {
            rtn.put("error", "Unknown user");
            return rtn;
        }
        rtn.put("success", true);
        Account acc = accs.iterator().next();
        rtn.put("user", SimpleFactory.getStormpathUser(acc));
        rtn.put("userInfo", acc.getCustomData());
        return rtn;
    }

    @RequestMapping(value = "create", method = RequestMethod.POST)
    public ResponseEntity<Object> createUser(final HttpServletRequest request,
                                             @RequestBody @Valid UserInfo user) {
        Client client = Stormpath.getClient(ServletUtilities.getConfigDir(request));
        HashMap<String,Object> rtn = new HashMap<String,Object>();
        rtn.put("success", false);  //assuming rtn will only be used on errors -- user is returned upon success
        if (client == null) {
            rtn.put("error", "Could not initiate Stormpath client");
            return new ResponseEntity<Object>(rtn, HttpStatus.BAD_REQUEST);
        }
        if ((user == null) || Util.isEmpty(user.email)) {
            rtn.put("error", "Bad/invalid user or email passed");
            return new ResponseEntity<Object>(rtn, HttpStatus.BAD_REQUEST);
        }
        if (log.isDebugEnabled()) log.debug("checking on stormpath for username=" + user.email);
        HashMap<String,Object> q = new HashMap<String,Object>();
        q.put("email", user.email);
        AccountList accs = Stormpath.getAccounts(client, q);
        if (accs.getSize() > 0) {
            rtn.put("error", "A user with this email already exists.");
            return new ResponseEntity<Object>(rtn, HttpStatus.BAD_REQUEST);
        }

        String givenName = "Unknown";
        if (!Util.isEmpty(user.fullName)) givenName = user.fullName;
        String surname = "-";
        int si = givenName.indexOf(" ");
        if (si > -1) {
            surname = givenName.substring(si+1);
            givenName = givenName.substring(0,si);
        }
        HashMap<String,Object> custom = new HashMap<String,Object>();
        custom.put("unverified", true);
        String errorMsg = null;
        Account acc = null;
        try {
            acc = Stormpath.createAccount(client, givenName, surname, user.email, Stormpath.randomInitialPassword(), null, custom);
            if (log.isDebugEnabled()) log.debug("successfully created Stormpath user for " + user.email);
        } catch (Exception ex) {
            if (log.isDebugEnabled()) log.debug("could not create Stormpath user for email=" + user.email + ": " + ex.toString());
            errorMsg = ex.toString();
        }
        if (errorMsg == null) {
            //acc.setStatus(AccountStatus.UNVERIFIED);  //seems to have no effect, but also not sure if this is cool by Stormpath
            return new ResponseEntity<Object>(SimpleFactory.getStormpathUser(acc), HttpStatus.OK);
        } else {
            rtn.put("error", "There was an error creating the new user: " + errorMsg);
            return new ResponseEntity<Object>(rtn, HttpStatus.BAD_REQUEST);
        }
        //return new ResponseEntity<Object>(user, HttpStatus.OK);
    }


/*
    @RequestMapping(value = "/get", method = RequestMethod.GET)
    public ResponseEntity<List<Survey>> save(final HttpServletRequest request) {
				PersistenceManager pm = getPM(request);
				Extent ext = pm.getExtent(Survey.class);
				Query q = pm.newQuery(ext);
        ArrayList all = new ArrayList((Collection) q.execute());
        return new ResponseEntity<List<Survey>>(all, HttpStatus.OK);
    }
*/


/*
    @RequestMapping(value = "/login/{username}/{password}", method = RequestMethod.POST)
    public ResponseEntity<MediaTag> appendMedia(final HttpServletRequest request,
                                              @RequestBody @Valid List<SinglePhotoVideo> media,
                                              @PathVariable("tagName") final String tagName) {

				PersistenceManager pm = getPM(request);
        MediaTag tag = null;
        try {
          tag = (MediaTag) pm.getObjectById(MediaTag.class, tagName);
        } catch (Exception ex) {
				}

				if (tag == null) {
        	tag = new MediaTag();
					tag.setName(tagName);
				}

				//for some reason, media does not get populated such that when tag is persisted, it creates all new SPVs.. grrr wtf.. explicitely loading them fixes this
				List<SinglePhotoVideo> med = new ArrayList<SinglePhotoVideo>();
				for (SinglePhotoVideo s : media) {
      		SinglePhotoVideo obj = ((SinglePhotoVideo) (pm.getObjectById(pm.newObjectIdInstance(SinglePhotoVideo.class, s.getDataCollectionEventID()), true)));
					if (obj != null) med.add(obj);
				}

				tag.addMedia(med);
				pm.makePersistent(tag);
        return new ResponseEntity<MediaTag>(tag, HttpStatus.OK);
    }
*/

    public static class UserInfo {
        public String email;
        public String fullName;
    }

    public static class UserVerifyInfo {
        public String email;
        public UserVerifyInfo() {
        }
        public String getEmail() {
            return email;
        }
    }

}

