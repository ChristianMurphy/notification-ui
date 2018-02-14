/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package uk.ac.ed.notify.service;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import uk.ac.ed.notify.entity.AuditActions;
import uk.ac.ed.notify.entity.ErrorCodes;
import uk.ac.ed.notify.entity.Notification;
import uk.ac.ed.notify.entity.NotificationError;
import uk.ac.ed.notify.entity.NotificationUser;
import uk.ac.ed.notify.entity.NotificationUserPK;
import uk.ac.ed.notify.learn.entity.Announcements;
import uk.ac.ed.notify.learn.entity.CourseUsers;
import uk.ac.ed.notify.learn.entity.Tasks;
import uk.ac.ed.notify.learn.entity.Users;
import uk.ac.ed.notify.learn.repository.LearnAnnouncementRepository;
import uk.ac.ed.notify.learn.repository.LearnAssessmentRepository;
import uk.ac.ed.notify.learn.repository.LearnCourseUserRepository;
import uk.ac.ed.notify.learn.repository.LearnGradebookGradeRepository;
import uk.ac.ed.notify.learn.repository.LearnTaskRepository;
import uk.ac.ed.notify.learn.repository.LearnUserRepository;
import uk.ac.ed.notify.repository.NotificationErrorRepository;
import uk.ac.ed.notify.repository.NotificationRepository;
import uk.ac.ed.notify.repository.UserNotificationAuditRepository;

/**
 *
 * @author agajda
 */
@Service
public class LearnService {
    private static final Logger logger = LoggerFactory.getLogger(LearnService.class);
    
    @Value("${notification.purge}")
    int purge;    
    
    @Autowired
    NotificationErrorRepository notificationErrorRepository;    
    
    @Autowired
    UserNotificationAuditRepository userNotificationAuditRepository;
    
    @Autowired
    LearnAnnouncementRepository learnAnnouncementRepository;
    
    @Autowired
    LearnTaskRepository learnTaskRepository;
    
    @Autowired
    LearnAssessmentRepository learnAssessmentRepository;
    
    @Autowired
    NotificationRepository notificationRepository;
    
    @Autowired
    LearnUserRepository learnUserRepository;
    
    @Autowired
    LearnCourseUserRepository learnCourseUserRepository;

    @Autowired
    LearnGradebookGradeRepository learnGradebookGradeRepository;
    
    private static final String announceForwardUrl = "/webapps/blackboard/execute/announcement?method=search&context=course&course_id=courseidtoreplace&handle=cp_announcements";
    private static final String taskForwardUrl = "/webapps/blackboard/execute/taskView?course_id=courseidtoreplace&task_id=taskidtoreplace";
    private static final String assignForwardUrl = "/webapps/assignment/uploadAssignment?content_id=contentidtoreplace&course_id=courseidtoreplace&assign_group_id=&mode=view";
    
    @Value("${learn.baseurl}")
    private String baseUrl; 
    
    @Value("${learn.notificationurl}")
    private String notificationUrl; 
    
    public interface Learn {
    	
    	String PUBLISHER_ID = "learn";
    	String TASK_TOPIC = "Learn Task";
    	String SYSTEM_TOPIC = "Learn System Announcement";
    	String COURSE_TOPIC = "Learn Course Announcement";
    	//String ASSESSMENT_TOPIC = "Learn Assessment";
    }

    
    /**
     * 
     * @param existingNotificationList list to search for notification
     * @param publisherNotificationId notification learn id to search against
     * @return existing notification or null
     */
    public Notification fetchNotification(List<Notification> existingNotificationList, String publisherNotificationId) {
    	
    	 Notification existingNotification = null;
         
         for(int i = 0; i < existingNotificationList.size(); i++){
             
             if(existingNotificationList.get(i).getPublisherNotificationId().equals(publisherNotificationId)){
                 existingNotification = existingNotificationList.get(i);
                 break;
             }            
         }
         
         return existingNotification;
    }
     
    public Notification assembleNotification (
    		String publisherId,
            String publisherNotificationId,
            String topic,
            String title,
            String body,
            String url,
            Date startDate,
            Date endDate,
            List<String> uuns,
            Notification notification) {
    	
    	if (notification.getNotificationId() == null){
    		/*
    		 * overwriting of these values disallowed for existing notifications
    		 */
    		notification.setPublisherId(publisherId);
        	notification.setPublisherNotificationId(publisherNotificationId);
        	notification.setTopic(topic);
    	}

        title = (title != null) ? title : "";
        notification.setTitle(title);
        
        body = (body == null || body.equals("")) ? title : body;
        notification.setBody(body);
        
        notification.setUrl(url);
        
        notification.setStartDate(startDate);
        //DTI020-21 if no end date is set, set it as 7 days after start date
        if(endDate == null){
            GregorianCalendar cal = new GregorianCalendar();
            cal.setTime(startDate);
            cal.add(Calendar.DATE, 7);
            endDate = cal.getTime();
        }
        notification.setEndDate(endDate);
        
        List<NotificationUser> notificationUsers = notification.getNotificationUsers();
        notificationUsers.clear();
        
        NotificationUser user = null;
        for (String uun : uuns){
        	user = new NotificationUser();
        	user.setId(new NotificationUserPK(notification.getNotificationId(), uun));
        	user.setNotification(notification);
        	notificationUsers.add(user);
        }
        
        notification.setNotificationUsers(notificationUsers);

        return notification;
    }
    
    /**
     * @param existingLearnNotifications List of all Learn notifications in Notification Backbone (where publisher id is learn)
     * @param topic
     * @return a list of Learn Notifications with given topic
     **/
    private List<Notification> getLearnNotificationsByTopic(List<Notification> existingLearnNotifications, String topic){
        List<Notification> notifications = new ArrayList<Notification>();

        for(int m = 0; m < existingLearnNotifications.size(); m++){

        	if(existingLearnNotifications.get(m).getTopic().equals(topic)){
        		notifications.add(existingLearnNotifications.get(m));	
        	}
        }
        return notifications;
    }    
    int index = 0;
    public void pullLearnNotifications(boolean triggerFromJob) {
        logger.info("pullLearnNotifications job started");
       
        logger.info("(A). Start querying learn ...");
        List<Users> activeLearnUsers = learnUserRepository.findAllActiveUsers();
        logger.info("Total number of active users in learn - " + activeLearnUsers.size());
        
        Map<Integer, String> userIdNamePair = new HashMap<Integer, String>();
        for(int i = 0; i < activeLearnUsers.size(); i++){
            userIdNamePair.put(activeLearnUsers.get(i).getPk1(), activeLearnUsers.get(i).getUserId());
        }
        
        logger.info("(B). Start querying notification backbone ...");
        List<Notification> existingLearnNotificationsList = notificationRepository.findByPublisherId(Learn.PUBLISHER_ID);
        logger.info("Total number of Learn notifications in Notification Backbone - " + existingLearnNotificationsList.size());
               
        List<String> processedLearnNotificationsList = new ArrayList<String>();
        
        // cache for bulk save
        Map<String,List<Notification>> actionsCache = new HashMap<String, List<Notification>>();
        actionsCache.put(AuditActions.CREATE_NOTIFICATION, new ArrayList<Notification>());
        actionsCache.put(AuditActions.UPDATE_NOTIFICATION, new ArrayList<Notification>());
        
        logger.info("(C). Start processing task, system announcements, course announcements ...");
        /*
         * Learn tasks
         */
        processLearnTasks(existingLearnNotificationsList, userIdNamePair, actionsCache, processedLearnNotificationsList);

        /*
         * System Announcements
         */
        processSystemAnnouncements(existingLearnNotificationsList, userIdNamePair, actionsCache, processedLearnNotificationsList);
        
        /*
         * Course announcements
         */
        processCourseAnnouncements(existingLearnNotificationsList, userIdNamePair, actionsCache, processedLearnNotificationsList);

        /*
         * Learn assignments ???
         */
        
        logger.info("Number of notifications to create " + actionsCache.get(AuditActions.CREATE_NOTIFICATION).size());
        logger.info("Number of notifications to update " +  actionsCache.get(AuditActions.UPDATE_NOTIFICATION).size());
        
        handleNotificationByBatch(AuditActions.CREATE_NOTIFICATION, actionsCache.get(AuditActions.CREATE_NOTIFICATION));
        handleNotificationByBatch(AuditActions.UPDATE_NOTIFICATION, actionsCache.get(AuditActions.UPDATE_NOTIFICATION));

        logger.info("STEP 4. ----------delete notification----------");                 
        logger.info("Total number of non-purgeable notifications in NB - " + existingLearnNotificationsList.size());
        logger.info("Total number of source items(sys, course announce, task, assessment) in Learn has been processed of this run - " + processedLearnNotificationsList.size());
        
        ArrayList<Notification> allCurrentNotificationsToBeDeleted = new ArrayList<Notification>();
        
        for(int i = 0; i < existingLearnNotificationsList.size(); i++){   
        	
            Notification notificationNB = existingLearnNotificationsList.get(i);
            if(!processedLearnNotificationsList.contains(notificationNB.getPublisherNotificationId())){
                
                Date deletableNotificationEndDate = notificationNB.getEndDate();
                if(deletableNotificationEndDate.after(new Date())){
                    allCurrentNotificationsToBeDeleted.add(notificationNB);
                }else{
                    logger.info("deletable, but its end date is already passed, ignore - " + deletableNotificationEndDate);
                }
            }
        }         
        logger.info("allCurrentNotificationsToBeDeleted - " + allCurrentNotificationsToBeDeleted.size());        
        handleNotificationByBatch(AuditActions.DELETE_NOTIFICATION, allCurrentNotificationsToBeDeleted);

        logger.info("pullLearnNotifications completed - no. of Learn Pull Job run since server started - [" + index++ + "]");                
    }
    
    /**
     * @params existingLearnNotificationsList contains all exisitng notifications in Notification Backbone
     *         userIdNamePair is a mapping of user primary key in Learn to uun
     *         actionsCache stores cached notifications for bulk insert and update
     *         pulledLearnNotificationsList keeps a record of all notifications processed in Learn pull job
     */
    public void processLearnTasks(List<Notification> existingLearnNotificationsList, Map<Integer, String> userIdNamePair, Map<String,List<Notification>> actionsCache, List<String> processedLearnNotificationsList) {
    	
    	logger.info("STEP.1 Processing Learn tasks");
    	
    	String topic = Learn.TASK_TOPIC;
    	
    	List<CourseUsers> courseUsersList = null;

        List<Tasks> learnTaskList = learnTaskRepository.findTasks();
        logger.info("Total number of tasks in Learn - " + learnTaskList.size());

        if (learnTaskList.size() > 0) {
        	List<Notification> notificationTaskList = getLearnNotificationsByTopic(existingLearnNotificationsList, topic);
            logger.info("Total number of task notifications in Notification Backbone - " + notificationTaskList.size());

            List<Integer> listOfTaskPks = new ArrayList<Integer>(); 
            
            Tasks task = null;
            Notification taskNotification = null;
            
            for (int i = 0; i < learnTaskList.size(); i++) {
                task = learnTaskList.get(i);          
                
                if(listOfTaskPks.contains(task.getPk1())){
                    continue;
                }
                listOfTaskPks.add(task.getPk1());
                
                /*
                 * publisherNotificationId
                 * unique learn notification identifier created by combining learn topic with learn task primary key
                 */
                String publisherNotificationId = topic + Integer.toString(task.getPk1());
                
                /*
                 * notification details
                 */
                String title = task.getSubject();
                String body = task.getDescription();
                Date endDate = task.getDueDate();
                /* 
                 * notification users
                 */
                courseUsersList = learnCourseUserRepository.findByCrsmainPk1(task.getCrsmainPk1());
                logger.info("Task identified by " + publisherNotificationId + " for course id - " + task.getCrsmainPk1() + " has " + courseUsersList.size() + " users.");
                
                List<String> uunList = new ArrayList<String>();
                String uun = null;
                
                for(int r = 0; r < courseUsersList.size(); r++){
                    uun = userIdNamePair.get(courseUsersList.get(r).getUsersPk1());
      
                    if(uun == null) continue;
                    
                    uunList.add(uun);
                }   

                /* 
                 * check if notification already exists (should be in persistence context)
                 */
                taskNotification = fetchNotification(notificationTaskList, publisherNotificationId);
                
                /*
                 * check persistence action
                 */
                String action = "";
                
                if (taskNotification == null) {
                	taskNotification = new Notification();
                	action = AuditActions.CREATE_NOTIFICATION;
                }
                else {
                	action = AuditActions.UPDATE_NOTIFICATION;
                }
                /*
                 * assemble notification
                 */
                
                //fix for WEB010-15 Learn Task start date is not set
                if(taskNotification.getStartDate() == null){
                    taskNotification.setStartDate(new Date()); 
                }
                
                taskNotification = assembleNotification(Learn.PUBLISHER_ID, 
                		publisherNotificationId, 
                		topic, 
                		title, 
                		body,
                		notificationUrl, 
                		taskNotification.getStartDate(), 
                		endDate, 
                		uunList, 
                		taskNotification);

                logger.info("Task notification identified by " + publisherNotificationId + " to be handled as " + action);
                
                actionsCache.get(action).add(taskNotification);
                
                processedLearnNotificationsList.add(publisherNotificationId);
            }  
        }
    }
    
    
    //--------------------------------------------------------------------------
    /*
    public String ifSaveLearnNotification(List<Notification> existingNotificationInNB, String publisherId, String publisherNotificationId, String uun, Notification notification) {

        Notification existingNotification = null;
        for(int i = 0; i < existingNotificationInNB.size(); i++){
            Notification noti = existingNotificationInNB.get(i);
            
            if(noti.getPublisherId().equals(publisherId) && noti.getPublisherNotificationId().equals(publisherNotificationId) && noti.getUun().equals(uun)){
                existingNotification = noti;
                break;
            }            
        }
        
        if (existingNotification == null) {
            notification.setNotificationId(null);
            logger.debug(notification.getTitle() + " insert");
            return "insert";
        } else {
            if (!notification.equals(existingNotification)) {
                notification.setNotificationId(existingNotification.getNotificationId());
                        
                logger.debug(notification.getTitle() + " update");
                return "update";
            }
        }
        
        logger.debug(notification.getTitle() + " ignore");
        return "ignore";
    }
    */
    
    
    public String ifSaveLearnNotification(List<Notification> existingNotificationInNB, 
            String publisherId, String publisherNotificationId, Notification notification) {

        Notification existingNotification = null;
        for(int i = 0; i < existingNotificationInNB.size(); i++){
            Notification noti = existingNotificationInNB.get(i);
            
            if(noti.getPublisherId().equals(publisherId) && noti.getPublisherNotificationId().equals(publisherNotificationId)){
                existingNotification = noti;
                break;
            }            
        }
        
        if (existingNotification == null) {
            notification.setNotificationId(null);
            logger.debug(notification.getTitle() + " insert");
            return "insert";
        } else {            
            if (!notification.equals(existingNotification)) {
                notification.setNotificationId(existingNotification.getNotificationId());
                
                logger.debug(notification.getTitle() + " update");
                return "update";
            }
        }

        logger.debug(notification.getTitle() + " ignore");
        return "ignore";
    }
    //--------------------------------------------------------------------------    
    
    
    
    public void processSystemAnnouncements(List<Notification> existingLearnNotificationsList, Map<Integer, String> userIdNamePair, Map<String,List<Notification>> actionsCache, List<String> processedLearnNotificationsList) {
    	
    	logger.info("STEP 2. Processing system announcements");
    	
    	String topic = Learn.SYSTEM_TOPIC;
    	
        List<Announcements> listOfSystemAnnouncements = learnAnnouncementRepository.findSystemAnnouncements();
        int systemAnnouncementsCount = listOfSystemAnnouncements.size();
        
        logger.info("Total number of system announcements in Learn - " + systemAnnouncementsCount);
        
        if (systemAnnouncementsCount > 0) {
        	
            List<Notification> systemNotificationsList = getLearnNotificationsByTopic(existingLearnNotificationsList, topic);
            logger.info("Total number of system announcements in Notification Backbone - " + systemNotificationsList.size());
            
            for (int i = 0; i < systemNotificationsList.size(); i++) {
                logger.info(i + "/" + systemNotificationsList.size() + " sysNotification - " + systemNotificationsList.get(i));
            }
            
           /*
            * System announcements are sent to all active learn users
            */
            List<String> uunList = new ArrayList<String>(userIdNamePair.values());
        	
            Notification systemNotification = null;
            Announcements systemAnnouncement = null;
        	
            for (int i = 0; i < systemAnnouncementsCount; i++) {
                logger.info(i + "/" + systemAnnouncementsCount + " ----------------------------");
                systemAnnouncement = listOfSystemAnnouncements.get(i);  
     
                Date includeStartDate = subtractDays(new Date(), purge);
                if(systemAnnouncement.getEndDate() == null && systemAnnouncement.getStartDate().before(includeStartDate) ){
                        //if this announcement has no end date and the start date is earlier than allowed start date, do not process
                        logger.info(systemAnnouncement.getStartDate() + " - do not process");
                        continue;                                                   
                }                
                
                String publisherNotificationId = topic + Integer.toString(systemAnnouncement.getPk1());     
                
                String title = systemAnnouncement.getSubject();
                String body = systemAnnouncement.getAnnouncement();
                Date startDate = systemAnnouncement.getStartDate();
                Date endDate = systemAnnouncement.getEndDate();
                                
                systemNotification = fetchNotification(systemNotificationsList, publisherNotificationId);
                String action = "";
                 
                String existingTitle = "";
                String existingBody = "";
                Date existingStartDate = null;
                Date existingEndDate = null;
                
                if(systemNotification == null) {
                    systemNotification = new Notification();
                    action = AuditActions.CREATE_NOTIFICATION;
                } else {
                    action = AuditActions.UPDATE_NOTIFICATION;
                        
                    existingTitle = systemNotification.getTitle();
                    existingBody = systemNotification.getBody();
                    existingStartDate = systemNotification.getStartDate();
                    existingEndDate = systemNotification.getEndDate();                        
                }
                
                logger.info("fetchNotification(existing or new) - " + systemNotification);                
                
                systemNotification = assembleNotification(Learn.PUBLISHER_ID, 
                		publisherNotificationId, 
                		topic, 
                		title, 
                		body, 
                		notificationUrl, 
                		startDate,  
                		endDate, 
                		uunList,
                		systemNotification);
                
                logger.info("assembleNotification(to be created) - " + systemNotification);
                
                if(ifEqual(
                    notificationUrl, existingTitle, existingBody, existingStartDate, existingEndDate,
                    notificationUrl, systemNotification.getTitle(), systemNotification.getBody(), systemNotification.getStartDate(), systemNotification.getEndDate() 
                )){
                    action = AuditActions.IGNORE_NOTIFICATION;
                }
                
                logger.info(action + " " + publisherNotificationId);  
                if(action.equals(AuditActions.CREATE_NOTIFICATION) || action.equals(AuditActions.UPDATE_NOTIFICATION)){                                   
                    logger.info("final systemNotification - " + systemNotification);  
                    actionsCache.get(action).add(systemNotification);                
                    processedLearnNotificationsList.add(publisherNotificationId);
                }                               
            }            
        }
    }
    
    private static String formatDate(Date date){
        try{
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-mm-dd HH:mm:ss");            
            return sdf.format(date);
        }catch(Exception e){
            return "0000-00-00 00:00:00";
        }
    }
    
    private static boolean ifEqual(
            String existingUrl, String existingTitle, String existingBody, Date existingStartDate, Date existingEndDate,
            String newUrl, String newTitle, String newBody, Date newStartDate, Date newEndDate
            ){

        logger.info(        
            "[1]" + existingUrl + "[2]" + existingTitle + "[3]" + existingBody + "[4]" + existingStartDate + "[5]" + existingEndDate + 
            "[6]" + newUrl + "[7]" + newTitle + "[8]" + newBody + "[9]" + newStartDate + "[10]" + newEndDate        
        );
           
        if (!existingUrl.equals(newUrl)) {        
            return false;
        }        
        if (!existingTitle.equals(newTitle)) {          
            return false;
        }
        if (!existingBody.equals(newBody)) {         
            return false;
        }
        if (!formatDate(existingStartDate).equals(formatDate(newStartDate))) {         
            return false;
        }
        if (!formatDate(existingEndDate).equals(formatDate(newEndDate))) {
            return false;
        }
 
        return true;
            
    }
    
    private static Date subtractDays(Date date, int days) {
		GregorianCalendar cal = new GregorianCalendar();
		cal.setTime(date);
		cal.add(Calendar.DATE, -days);
				
		return cal.getTime();
    }    
    
    public void processCourseAnnouncements(List<Notification> existingLearnNotificationsList, Map<Integer, String> userIdNamePair, Map<String,List<Notification>> actionsCache, List<String> processedLearnNotificationsList) {
    	
    	   logger.info("STEP 3. Processing course announcements"); 
    	   
    	   String topic = Learn.COURSE_TOPIC;
    	   
           List<Announcements> listOfCourseAnnouncements = learnAnnouncementRepository.findCourseAnnouncements();
           logger.info("Total number of course announcements in Learn - " + listOfCourseAnnouncements.size());
    
           if (listOfCourseAnnouncements.size() > 0) {
        	   
               List<Notification> courseNotificationsList = getLearnNotificationsByTopic(existingLearnNotificationsList, topic);
               logger.info("Total number of course announcements in Notification Backbone - " + courseNotificationsList.size());
               
               ArrayList<Integer> listOfCourseAnnouncementPks = new ArrayList<Integer>(); 
               
               Announcements courseAnnouncement = null;
               List<CourseUsers> courseUsersList = null;
               Notification courseNotification = null;
               
               int size = listOfCourseAnnouncements.size();
               for (int i = 0; i < size; i++) {

                   courseAnnouncement = listOfCourseAnnouncements.get(i);

                   Date includeStartDate = subtractDays(new Date(), purge);
                   if(courseAnnouncement.getEndDate() == null && courseAnnouncement.getStartDate().before(includeStartDate) ){
                       //if this announcement has no end date and the start date is earlier than allowed start date, do not process
                       //logger.info(i + "/" + size + "--------------------" + courseAnnouncement.getStartDate() + " - do not process");
                       continue;                       
                   }else{
                       logger.info(i + "/" + size + "-------------------- process-" + courseAnnouncement);
                   }
                   
                   if(listOfCourseAnnouncementPks.contains(courseAnnouncement.getPk1())){
                       logger.info("listOfCourseAnnouncementPks already contains, ignore");
                       continue;
                   }
                   listOfCourseAnnouncementPks.add(courseAnnouncement.getPk1());

                   String publisherNotificationId = topic + Integer.toString(courseAnnouncement.getPk1());        
                   
                   String title = courseAnnouncement.getSubject();
                   String body = courseAnnouncement.getAnnouncement();
                   Date startDate = courseAnnouncement.getStartDate();
                   Date endDate = courseAnnouncement.getEndDate();
                   
                   courseUsersList = learnCourseUserRepository.findByCrsmainPk1(courseAnnouncement.getCrsmainPk1());
                   logger.info("Course announcement identified by " + publisherNotificationId + " has " + courseUsersList.size() + " users.");
                   
                   logger.info("announcement - " + courseAnnouncement);                   
                   
                   List<String> uunList = new ArrayList<String>();
                   String uun = null;
                   
                  for(int r = 0; r < courseUsersList.size(); r++){
                	  uun = userIdNamePair.get(courseUsersList.get(r).getUsersPk1());
         
                      if(uun == null) continue;
                       
                      uunList.add(uun);
                  }   
                  logger.info("uunList - " + uunList);                  

                  courseNotification = fetchNotification(courseNotificationsList, publisherNotificationId); 

                  String action = "";
                 
                  String existingTitle = "";
                  String existingBody = "";
                  Date existingStartDate = null;
                  Date existingEndDate = null;
                                  
                  
                  if (courseNotification == null) {
                    courseNotification = new Notification();
                    action = AuditActions.CREATE_NOTIFICATION;
                  }
                  else {
                    action = AuditActions.UPDATE_NOTIFICATION;
                  
                    existingTitle = courseNotification.getTitle();
                    existingBody = courseNotification.getBody();
                    existingStartDate = courseNotification.getStartDate();
                    existingEndDate = courseNotification.getEndDate();                      
                  }
                  logger.info("fetchNotification(existing or new) - " + courseNotification);      
                  
                  courseNotification = assembleNotification(Learn.PUBLISHER_ID, publisherNotificationId, topic, title, body, notificationUrl, startDate, endDate, uunList, courseNotification); 
                  logger.info("assembleNotification(to be created) - " + courseNotification);
                
                  if(ifEqual(
                    notificationUrl, existingTitle, existingBody, existingStartDate, existingEndDate,
                    notificationUrl, courseNotification.getTitle(), courseNotification.getBody(), courseNotification.getStartDate(), courseNotification.getEndDate() 
                  )){
                    action = AuditActions.IGNORE_NOTIFICATION;
                  }
                  
                  logger.info(action + " " + publisherNotificationId);  
                  if(action.equals(AuditActions.CREATE_NOTIFICATION) || action.equals(AuditActions.UPDATE_NOTIFICATION)){                                   
                     logger.info("final courseNotification - " + courseNotification);  
                     actionsCache.get(action).add(courseNotification);                  
                     processedLearnNotificationsList.add(publisherNotificationId); 
                  }                  
                  
           
               }
           } 
    }
    
    public void handleNotificationByBatch(String action, List<Notification> notifications) {
            try{
            	if (notifications != null && !notifications.isEmpty()) {
            		
            		if(action.equals(AuditActions.CREATE_NOTIFICATION) || action.equals(AuditActions.UPDATE_NOTIFICATION)){                      
                            logger.info("notifications is not empty - total notifications - " + notifications.size());
                            
                            List<Notification> notificationsToAction = new ArrayList<Notification>();
                            for(int i = 0; i < notifications.size(); i++){
                                Notification notification = notifications.get(i);
                                if( notification.getBody() == null || notification.getBody().equals("") ){
                                    notification.setBody(notification.getTitle());
                                }
                                notificationsToAction.add(notification);
                            }
                                                                                    
                            //notificationRepository.bulkSave(notificationsToAction);
                            
                            int size = notificationsToAction.size();                            
                            logger.info("start inserting or updating, this may take a while - [" + action + "] size - [" + size + "]");                                                        
                            for(int i = 0; i < size; i++){
                                logger.info(i + "/" + size + " save - " + notificationsToAction.get(i));
                                notificationRepository.save(notificationsToAction.get(i));
                            }
                            
                            
                            logger.info("finished... ");

                    }else if(action.equals(AuditActions.DELETE_NOTIFICATION)){                      
                        //notificationRepository.delete(notifications);
                        
                        logger.info("deleting notifications (setting end dates) - " +  notifications.size());
                        for(int i = 0; i < notifications.size(); i++){
                            notifications.get(i).setEndDate(new Date());
                        }
                        
                        //notificationRepository.bulkSave(notifications);
                        for(int i = 0; i < notifications.size(); i++){
                            logger.info("process " + i + " - " + notifications.get(i));
                            notificationRepository.save(notifications.get(i));
                            logger.info("done");
                        }
                        
                        
                        logger.info("bulk save complete");
                    } 
            	}
            }catch(Exception e){
                 if(action.equals(AuditActions.CREATE_NOTIFICATION)){
                     logErrorNotification(ErrorCodes.SAVE_ERROR ,e);                      
                 }else if(action.equals(AuditActions.UPDATE_NOTIFICATION)){
                     logErrorNotification(ErrorCodes.UPDATE_ERROR ,e);                      
                 }else if(action.equals(AuditActions.DELETE_NOTIFICATION)){
                     logErrorNotification(ErrorCodes.DELETE_ERROR ,e); 
                 }
            }
    }    
     
    /**
     * 
     * @param errorCode ErrorCodes
     * @param e
     */
    private void logErrorNotification(String errorCode, Exception e){
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            e.printStackTrace(pw);
            String stacktrace = e.toString();  
        
            NotificationError notificationError = new NotificationError();
            notificationError.setErrorCode(errorCode);
            notificationError.setErrorDescription(e.toString() + e.getMessage());
            notificationError.setErrorDate(new Date());
            notificationErrorRepository.save(notificationError);        
    }
}
