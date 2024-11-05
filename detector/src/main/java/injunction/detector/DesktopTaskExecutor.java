package injunction.detector;

/**
 * DesktopTaskExecutor:
 * <p>
 * <p>
 * Created by James X. Nelson (James@WeTheInter.net) on 04/11/2024 @ 11:14 p.m.
 */
public class DesktopTaskExecutor implements TaskExecutor {
    @Override
    public void execute(Runnable task) {
        new Thread(task).start();
    }
}
