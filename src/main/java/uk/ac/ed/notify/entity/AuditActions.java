package uk.ac.ed.notify.entity;

/**
 * Created by rgood on 21/10/2015.
 */
public interface AuditActions {

    String CREATE_NOTIFICATION = "PUBLISH_NOTIFICATION";
    String UPDATE_NOTIFICATION = "UPDATE_NOTIFICATION";
    String DELETE_NOTIFICATION = "DELETE_NOTIFICATION";
    String CLEANED_HTML = "CLEANED_HTML";

    String IGNORE_NOTIFICATION = "IGNORE_NOTIFICATION";

    /*
     * Actions associated with delivery channels
     */
    String EMAIL_NOTIFICATION = "EMAIL_NOTIFICATION";
    String TEXT_NOTIFICATION = "TEXT_NOTIFICATION";

}
