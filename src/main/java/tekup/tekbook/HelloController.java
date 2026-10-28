package tekup.tekbook;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
public class HelloController {

    @GetMapping("/")
    public String hello() {
        return "tekbook-app up";
    }

    @GetMapping("/api/books")
    public List<Map<String, Object>> books() {
        return List.of(
            Map.of("id", 1, "title", "Clean Code", "author", "Robert C. Martin"),
            Map.of("id", 2, "title", "The Pragmatic Programmer", "author", "Hunt & Thomas")
        );
    }
}
