package matching;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.util.UUID;

// 액션 생성 클래스
public class ActionCreator {
    public String connectAddress(){
        JSONObject object = new JSONObject();
        object.put("type", "CONNECT_SUCCESS");
        JSONObject content = new JSONObject();
        content.put("port", 5003);
        content.put("uuid", UUID.randomUUID().toString());
        object.put("payload", content);

        return object.toJSONString();
    }


}
