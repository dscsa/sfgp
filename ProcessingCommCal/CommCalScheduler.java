global class CommCalScheduler implements Schedulable {

   public static String TEST_CRON_DATE = '0 0 0 3 9 ? 2022'; //only used for the scheduler test class

   global void execute(SchedulableContext SC) {
      System.debug('called schedulable execute');
      BatchCommCalProcessing b = new BatchCommCalProcessing();
      b.processTasks();
   }
}
