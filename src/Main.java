import java.io.IOException;
import java.util.concurrent.TimeUnit;

public class Main {
    public static void main(String[] args) throws IOException {
        CrptApi crptApi = new CrptApi(TimeUnit.SECONDS, 1);
        crptApi.runServer();
    }
}