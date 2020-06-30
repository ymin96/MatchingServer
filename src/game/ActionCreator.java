package game;

import org.json.simple.JSONObject;

public class ActionCreator {

    public String readyStatusFinish(String uuid){
        JSONObject object = new JSONObject();
        object.put("type","READY_STATUS_FINISH");
        JSONObject payload = new JSONObject();
        payload.put("uuid",uuid);
        object.put("payload", payload);

        return object.toJSONString();
    }
}
