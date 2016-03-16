package logbook.api;

import javax.json.JsonObject;

import logbook.bean.AppCondition;
import logbook.bean.BattleMidnightSpMidnight;
import logbook.internal.log.BattleLog;
import logbook.proxy.RequestMetaData;
import logbook.proxy.ResponseMetaData;

/**
 * /kcsapi/api_req_battle_midnight/sp_midnight
 *
 */
@API("/kcsapi/api_req_battle_midnight/sp_midnight")
public class ApiReqBattleMidnightSpMidnight implements APIListenerSpi {

    @Override
    public void accept(JsonObject json, RequestMetaData req, ResponseMetaData res) {
        JsonObject data = json.getJsonObject("api_data");
        if (data != null) {

            BattleLog log = AppCondition.get().getBattleResult();
            if (log != null) {
                log.setBattle(BattleMidnightSpMidnight.toBattle(data));
            }
        }
    }

}
