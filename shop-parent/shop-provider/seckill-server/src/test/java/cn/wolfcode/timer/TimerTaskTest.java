package cn.wolfcode.timer;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.TimerTask;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class TimerTaskTest {
    @Test
    public void test() throws InterruptedException {
        TimerTask timerTask = new TimerTask() {

            @Override
            public void run() {
                System.out.println("当前时间："+ LocalDateTime.now());
            }
        };
        ScheduledExecutorService service = Executors.newScheduledThreadPool(10);
        service.scheduleWithFixedDelay(timerTask,0,2,TimeUnit.SECONDS);
        TimeUnit.SECONDS.sleep(9999);

    }
}
