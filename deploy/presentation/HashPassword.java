import java.io.BufferedReader;
import java.io.InputStreamReader;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
class HashPassword {
    public static void main(String[] args) throws Exception {
        var reader = new BufferedReader(new InputStreamReader(System.in));
        var encoder = new BCryptPasswordEncoder(10);
        String value;
        while ((value = reader.readLine()) != null) System.out.println(encoder.encode(value));
    }
}
