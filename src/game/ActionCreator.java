package game;

import org.json.simple.JSONObject;

public class ActionCreator {

    public String readyStatusFinish(String uuid, Boolean turn){
        JSONObject object = new JSONObject();
        object.put("type","READY_STATUS_FINISH");
        JSONObject payload = new JSONObject();
        payload.put("uuid",uuid);
        payload.put("turn", turn);
        object.put("payload", payload);

        return object.toJSONString();
    }

    public String receiveAction(String uuid, JSONObject action, boolean turn){
        JSONObject result = new JSONObject();
        result.put("type", "RECEIVE_ACTION");
        JSONObject payload = new JSONObject();
        payload.put("uuid", uuid);
        payload.put("action", action);
        payload.put("turn", turn);
        result.put("payload", payload);
        return result.toJSONString();
    }

    public String receiveFailAction(String uuid, JSONObject action, boolean win){
        JSONObject result = new JSONObject();
        result.put("type","RECEIVE_FAIL_ACTION");
        JSONObject payload = new JSONObject();
        payload.put("uuid", uuid);
        payload.put("action", action);
        payload.put("win", win);
        result.put("payload", payload);
        return result.toJSONString();
    }
}
