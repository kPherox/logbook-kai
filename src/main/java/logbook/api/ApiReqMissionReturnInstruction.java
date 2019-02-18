package logbook.api;

import java.util.Map;

import javax.json.JsonObject;

import logbook.bean.DeckPort;
import logbook.bean.DeckPortCollection;
import logbook.internal.JsonHelper;
import logbook.proxy.RequestMetaData;
import logbook.proxy.ResponseMetaData;

/**
 * /kcsapi/api_req_mission/return_instruction
 *
 */
@API("/kcsapi/api_req_mission/return_instruction")
public class ApiReqMissionReturnInstruction implements APIListenerSpi {

    @Override
    public void accept(JsonObject json, RequestMetaData req, ResponseMetaData res) {

        JsonObject data = json.getJsonObject("api_data");
        if (data != null) {
            Integer deckId = Integer.valueOf(req.getParameter("api_deck_id"));
            Map<Integer, DeckPort> deckMap = DeckPortCollection.get()
                    .getDeckPortMap();
            DeckPort oldDeck = deckMap.get(deckId);
            DeckPort newDeck = oldDeck.clone();
            JsonHelper.bind(data).set("api_mission", newDeck::setMission, JsonHelper::toLongList);
            deckMap.put(deckId, newDeck);

            DeckPortCollection.get()
                    .setDeckPortMap(deckMap);
        }
    }
}
