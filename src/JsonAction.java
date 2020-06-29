import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

public class JsonAction {
    public String connectAddress(String address, String host){
        JSONObject object = new JSONObject();
        object.put("type", "CONNECT_ADDRESS");
        JSONObject content = new JSONObject();
        content.put("address", address);
        if (host == null)
            content.put("me", "client");
        else
            content.put("me", "host");
        object.put("payload", content);

        return object.toJSONString();
    }
}
