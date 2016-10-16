package mage.verify;

import mage.cards.ExpansionSet;
import mage.cards.Sets;
import org.junit.Test;

import java.io.IOException;
import java.util.Map;

public class VerifySetDataTest {

    @Test
    public void verifySets() throws IOException {
        Map<String, JsonSet> reference = MtgJson.sets();

        for (ExpansionSet set : Sets.getInstance().values()) {
            JsonSet ref = reference.get(set.getCode());
            if (ref == null) {
                for (JsonSet js : reference.values()) {
                    if (set.getCode().equals(js.oldCode) || set.getCode().toLowerCase().equals(js.magicCardsInfoCode)) {
                        ref = js;
                        break;
                    }
                }
                if (ref == null) {
                    System.out.println("missing reference for " + set);
                    continue;
                }
            }
            if (!String.format("%tF", set.getReleaseDate()).equals(ref.releaseDate)) {
                System.out.printf("%40s %-20s %20tF %20s%n", set, "release date", set.getReleaseDate(), ref.releaseDate);
            }
            if (set.hasBoosters() != (ref.booster != null)) {
                System.out.printf("%40s %-20s %20s %20s%n", set, "has boosters", set.hasBoosters(), ref.booster != null);
            }
            boolean refHasBasicLands = false;
            for (JsonCard card : ref.cards) {
                if ("Mountain".equals(card.name)) {
                    refHasBasicLands = true;
                    break;
                }
            }
            if (set.hasBasicLands() != refHasBasicLands) {
                System.out.printf("%40s %-20s %20s %20s%n", set, "has basic lands", set.hasBasicLands(), refHasBasicLands);
            }
        }
    }

}
