public class BatchCommCalProcessing {

    public class MyException extends Exception {}

    private GP_User__c[] allUsers = null;

    String FAILSAFE_GPUSER_ID = 'Kiah'; //These will be turned to Ids when collectEssentialUserIds runs
    String TASK_OWNER_ID = 'Comm-Calendar';
    String DEBUG_OWNER_ID = 'Aminata';
    String GP_OWNER_ID = 'GoodPill Support';

    public void processTasks(){

    	System.debug('running process!');

        Boolean allGood = collectEssentialUserIds();

        if(allGood){

            Task[] newTasks = new Task[0];
            Event[] processedEvents = new Event[0];
            DateTime cutoff = DateTime.now().addMinutes(-15);
            System.debug('cutoff: ' + cutoff);

            Event[] eventsToProcess = [
                SELECT StartDateTime, Type, Subject, Description
                FROM Event
                WHERE (StartDateTime <= :DateTime.now()) AND (StartDateTime >= :cutoff) AND (Type = null)
            ];

            System.debug('going to try to process ' + eventsToProcess.size() + ' events');

            for(Event e : eventsToProcess){
                if((e.Description != null) && (e.Description.contains('---SFOBJECT---'))){ //easier to exclude these here, as opposed to require RegEx in SOQL
                    System.debug('processing: ' + e.Subject);
                    Task result = processEvent(e);
                    newTasks.add(result);
                    e.Type = 'Comm-Calendar';
                    processedEvents.add(e);
                }
            }

            System.debug('saving ' + newTasks.size() + ' tasks');
            System.debug('saving ' + processedEvents.size() + ' events');

            try{
            	insert newTasks;
            	update processedEvents;
            } Catch(Exception e){
                System.debug('Fatal error either adding tasks or saving event updates: ' + e.getMessage());
            	Task errorTask = new Task(
                    Subject = '[Critical CommCal Error] Saving/Updating',
                    Description = e.getMessage(),
                    WhatId = DEBUG_OWNER_ID,
                    Assigned_To__c = DEBUG_OWNER_ID
        		);
            	insert errorTask;
            }


        } else {
            System.debug('error getting users, this should never happen');
        }

    }


    public Task processEvent(Event event){

        //---------Fields extracted from the commObject-------------
        Map<String, String> commObj = null;
        String raw = event.Description;

        try{
            String extractedSFObjectString = raw.substring(0,raw.indexOf('---SFOBJECT---')).trim().replace('\n','\\n').replace('\r','\\r');
        	System.debug('extracted string of sf object:' + extractedSFObjectString);
        	commObj = (Map<String, String>)JSON.deserialize(extractedSFObjectString, Map<String, String>.class);
        }catch(Exception e){
            String failure_message = 'failed to parse description field of event: ' + e.getMessage() + '\n\n' + raw;
            System.debug(failure_message);
			Task errorTask = new Task(
                Subject = '[Critical CommCal Error]',
                Description = failure_message,
                WhatId = DEBUG_OWNER_ID,
                Assigned_To__c = DEBUG_OWNER_ID
        	);
            return errorTask;
        }

        String subject = commObj.get('subject');
        String body = commObj.get('body');
        String contact = commObj.get('contact');
        String assignTo = commObj.get('assign_to');
        String dueDate = commObj.get('due_date');

        //---------Local versions we need to determine and/or save-------------
        String contactID = '';
        String status = 'Completed';
        String assignedUserId = '';
        Date due_date = null;
        List<String> follow_ups = new List<String>();

        if((subject == null) || (body == null) || (contact == null)){

            follow_ups.add('\n\nMISSING REQUIRED FIELD(S) (subject, body, contact)\nComm Object:   ' + raw);
            System.debug('Incomplete Comm object');
           	//assignedUserId = FAILSAFE_GPUSER_ID;

        } else {

            //-------------If assign_to field give, need to find the GP User------------
            if(assignTo != null){
                assignedUserId = getGPUserId(assignTo);
                if(assignedUserId.length() == 0){
                    System.debug('No GP user to assign to found');
                    follow_ups.add('No GP user found matching the provided assign_to field: ' + assignTo);
                    //assignedUserId = FAILSAFE_GPUSER_ID;
                }
            }


            //-------------We need to get the contact object using the name------------
            try{
                Contact vContact = null;

                if(isPhone(contact)){ //search by phone
                    vContact = [SELECT Id, Name
                    FROM Contact
                    WHERE gp_phone1__c = :cleanPhone(contact)
                    LIMIT 1];
                } else if(isEmail(contact)){ //search by email
                    vContact = [SELECT Id, Name
                    FROM Contact
                    WHERE gp_email__c = :cleanEmail(contact)
                    LIMIT 1];
                } else { //search by name
					vContact = [SELECT Id, Name
                    FROM Contact
                    WHERE Name = :contact
                    LIMIT 1];
                }

                System.debug('Contact found:' + vContact.Name);
                contactID = vContact.Id;

            }catch(QueryException err){
                //if none found then override assign-to and assign to Kiah with followup to note
                System.debug('No matching contact found: ' + err.getMessage());
                follow_ups.add('No contact found matching the provided contact field: ' + contact);
            }
        }

		//-----------If we need to add a due date-------
        if(dueDate != null){
            try{
                List<String> dateArr = dueDate.split('-'); //AK gives YYYY-MM-DD format, conver to MM/DD/YYYY bc Apex is silly
                if(dateArr.size() != 3) throw new MyException('Date Cannot be parsed');
                String reformatedDate = dateArr[1] + '/' + dateArr[2] + '/' + dateArr[0];
            	due_date = Date.parse(reformatedDate);
                status = (due_date >= Date.today()) ? 'In Progress' : 'Completed';
            } catch(Exception e){
                System.debug('Couldnt parse due date field');
                follow_ups.add('Couldnt parse given due_date: ' + dueDate);
                //if(assignedUserId.length() == 0) assignedUserId = FAILSAFE_GPUSER_ID; //someone should know if this happens, so catch failsafe
            }
        } else { //then status will remain completed, want to make the due date today
            due_date = Date.today();
        }

		//-----------If we need to incorporate a follow-up bc of an error-------
        if(follow_ups.size() > 0){
            status = 'In Progress'; //dont let it slip by
            if(assignedUserId.length() == 0) assignedUserId = FAILSAFE_GPUSER_ID; //someone should know if this happens, so catch failsafe
            String follow_up_string = String.join(follow_ups,'\n');
            body += '\n\n----------Follow Up Notes--------------\n\nIssue(s) Processing:\n' + follow_up_string;
        }

		//-----------Finally actually create and save the task------
        System.debug('assigned id: ' + assignedUserId);

        Task result = new Task(
                Subject = subject,
                Description = body,
                WhoId = (contactID.length() > 0) ? contactId : null,
                Status = status,
               	WhatId = TASK_OWNER_ID,
            	OwnerID = GP_OWNER_ID,
                Assigned_To__c = (assignedUserId.length() > 0) ? assignedUserId : null,
                ActivityDate = due_date
        	);
        System.debug('created full task to return:' + result);
		return result;
    }

    //----------------------Helpers----------------------------------------

    public Boolean collectEssentialUserIds(){ //because we always need the three essential IDs, just do them in one go. its the little things
		allUsers = gatherAllGPUsers(); //Collect all GPUsers in one query to minimize SOQL calls

        getGPOwnerID();

        for(GP_User__c user : allUsers){
            if(user.Name == FAILSAFE_GPUSER_ID){
                FAILSAFE_GPUSER_ID = user.Id;
            } else if(user.Name == TASK_OWNER_ID){
                TASK_OWNER_ID = user.Id;
            } else if(user.Name == DEBUG_OWNER_ID){
                DEBUG_OWNER_ID = user.Id;
            }
        }

        return allUsers.size() > 0; //otherwise big error somewhere
    }

    public void getGPOwnerID(){
        User result = null;

        try{
            result = [SELECT Id, Name
                        FROM User
                     	WHERE Name = :GP_OWNER_ID
                     	LIMIT 1];
            //System.debug('GP users found:' + result);
        }catch(QueryException err){
            System.debug('error with query for all users: ' + err.getMessage());
        }

        GP_OWNER_ID = result.Id;
    }

    public String getGPUserId(String name){
        for(GP_User__c user : allUsers){
            if(user.Name == name) return user.Id;
        }
        return '';
    }

    public GP_User__c[] gatherAllGPUsers(){
        GP_User__c[] result = new GP_User__C[0];

        try{
            result = [SELECT Id, Name
                        FROM GP_User__c];
            //System.debug('GP users found:' + result);
        }catch(QueryException err){
            System.debug('error with query for all users: ' + err.getMessage());
        }

        return result;
    }

    //V1 of phone & email text cleaning & checking.
    //Could be expanded to include more formats down the line if needed

    public Boolean isPhone(String str){
        return cleanPhone(str).isNumeric();
    }

    public Boolean isEmail(String str){
        return cleanEmail(str).contains('@');
    }

    public String cleanPhone(String str){
        return str.replace('(', '').replace(')', '').replace('-', '').deleteWhitespace();
    }

    public String cleanEmail(String str){
        return str.deleteWhitespace();
    }


}
