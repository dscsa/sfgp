@isTest(SeeAllData=true)
class TestBatchCommCalProcessing {
	public static String TEST_CRON_DATE = '0 0 0 3 9 ? 2022';
    public static String DATETIME_TEST_DATE = '2022-09-03 00:00:00';

	static testmethod void test() {

		Test.startTest();
		Event[] testEvents = new Event[0];

        testEvents.add(new Event(
            Subject = 'full and successful example to do',
            Description = '{"subject":"full and successful example to do","body":"Drug Tablet Delayed Release in Order needs to be switched","contact":"6555555555","assign_to":"Aminata","due_date":"2020-10-16"}---SFOBJECT---[{"subject": "Order cannot be matched by GSN","body": "Drug Tablet Delayed Release in Order needs to be switched","contact": "655555555","assign_to": "Adam","due_date": "2020-05-14"}]',
        	StartDateTime = DateTime.now().addMinutes(-10),
            DurationInMinutes = 30
        ));

        testEvents.add(new Event(
            Subject = 'full and successful example completed',
            Description = '{"subject":"full and successful example complete","body":"Drug Tablet Delayed Release in Order needs to be switched","contact":"ami@ sirum.org","assign_to":"Kiah","due_date":"2020-02-16"}---SFOBJECT---[{"subject": "Order #39 cannot be matched by GSN","body": "Drug Tablet Delayed Release in Order needs to be switched","contact": "655555555","assign_to": "Adam","due_date": "2020-05-14"}]',
        	StartDateTime = DateTime.now().addMinutes(-10),
            DurationInMinutes = 30

        ));

        testEvents.add(new Event(
            Subject = 'should skip this event',
            Description = 'bazaar',
            StartDateTime = DateTime.now().addMinutes(-10),
            DurationInMinutes = 30
        ));

        testEvents.add(new Event(
            Subject = 'missing extracted sf object tag',
            Description = '{"subject":"full and successful example complete","body":"Drug Tablet Delayed Release in Order needs to be switched","contact":"655 555 5555","assign_to":"Jessica","due_date":"2020-02-16"}---SFO',
            StartDateTime = DateTime.now().addMinutes(-10),
            DurationInMinutes = 30
        ));

        testEvents.add(new Event(
            Subject = 'missing fields that are optional',
            Description = '{"subject":"full and successful example complete","body":"Drug Tablet Delayed Release in Order needs to be switched","contact":"Test ALSOTEST"}---SFOBJECT---[{"subject": "Order  cannot be matched by GSN","body": "Drug Tablet Delayed Release in Order needs to be switched","contact": "655555555","assign_to": "Adam","due_date": "2020-05-14"}]',
            StartDateTime = DateTime.now().addMinutes(-10),
            DurationInMinutes = 30
        ));


        testEvents.add(new Event(
            Subject = 'misformated description',
            Description = 'DESCRIP{"subject":"full and successful example to do","body":"Drug Tablet Delayed Release in Order needs to be switched","contact":"655-555-5555","assign_to":"Aminata","due_date":"2020-10-16"}---SFOBJECT---[{"subject": "Order cannot be matched by GSN","body": "Drug Tablet Delayed Release in Order needs to be switched","contact": "655555555","assign_to": "Adam","due_date": "2020-05-14"}]',
            StartDateTime = DateTime.now().addMinutes(-10),
            DurationInMinutes = 30
        ));

        testEvents.add(new Event(
            Subject = 'missing required',
            Description = '{"subject":"missing required","contact":"655 555 5555","assign_to":"Aminata","due_date":"2020-10-16"}---SFOBJECT---[{"subject": "Order cannot be matched by GSN","body": "Drug Tablet Delayed Release in Order needs to be switched","contact": "655555555","assign_to": "Adam","due_date": "2020-05-14"}]',
            StartDateTime = DateTime.now().addMinutes(-10),
            DurationInMinutes = 30
        ));

        testEvents.add(new Event(
            Subject = 'misformated date',
            Description = '{"subject":"full and successful example complete","body":"Drug Tablet Delayed Release in Order needs to be switched","contact":"(655)555 5555","assign_to":"Jessica","due_date":"2020/8-2"}---SFOBJECT---[{"subject": "Order cannot be matched by GSN","body": "Drug Tablet Delayed Release in Order needs to be switched","contact": "655555555","assign_to": "Adam","due_date": "2020-05-14"}]',
            StartDateTime = DateTime.now().addMinutes(-10),
            DurationInMinutes = 30
        ));

        insert testEvents;

        String jobId = System.schedule('CommCalScheduler',
         CommCalScheduler.TEST_CRON_DATE,
         new CommCalScheduler());

        // Get the information from the CronTrigger API object
        CronTrigger ct = [SELECT Id, CronExpression, TimesTriggered,
         NextFireTime
         FROM CronTrigger WHERE id = :jobId];

        // Verify the expressions are the same
        System.assertEquals(CommCalScheduler.TEST_CRON_DATE,
         ct.CronExpression);

        // Verify the job has not run
        System.assertEquals(0, ct.TimesTriggered);

        // Verify the next time the job will run
        System.assertEquals(DATETIME_TEST_DATE,
         String.valueOf(ct.NextFireTime));

   		Test.stopTest();

   }
}
