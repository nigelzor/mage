package mage.verify;

import mage.cards.Card;
import mage.cards.SplitCard;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

public class CompareCardText {

    private static int levenshteinDistance(CharSequence lhs, CharSequence rhs) {
        int len0 = lhs.length() + 1;
        int len1 = rhs.length() + 1;

        // the array of distances
        int[] cost = new int[len0];
        int[] newcost = new int[len0];

        // initial cost of skipping prefix in String s0
        for (int i = 0; i < len0; i++) cost[i] = i;

        // dynamically computing the array of distances

        // transformation cost for each letter in s1
        for (int j = 1; j < len1; j++) {
            // initial cost of skipping prefix in String s1
            newcost[0] = j;

            // transformation cost for each letter in s0
            for(int i = 1; i < len0; i++) {
                // matching current letters in both strings
                int match = (lhs.charAt(i - 1) == rhs.charAt(j - 1)) ? 0 : 1;

                // computing cost for each transformation
                int cost_replace = cost[i - 1] + match;
                int cost_insert  = cost[i] + 1;
                int cost_delete  = newcost[i - 1] + 1;

                // keep minimum cost
                newcost[i] = Math.min(Math.min(cost_insert, cost_delete), cost_replace);
            }

            // swap cost/newcost arrays
            int[] swap = cost; cost = newcost; newcost = swap;
        }

        // the distance is the cost for transforming all letters in both strings
        return cost[len0 - 1];
    }

    public static void main(String[] args) throws IOException {
        SortedMap<String, List<String>> all = new TreeMap<>();
        for (Card card : CompareWithMtgJsonTest.allCards()) {
            if (card.isSplitCard()) {
                addRules(all, ((SplitCard) card).getLeftHalfCard());
                addRules(all, ((SplitCard) card).getRightHalfCard());
            } else {
                addRules(all, card);
            }
        }

        BufferedWriter refOutput = Files.newBufferedWriter(new File("cards_reference").toPath(), StandardCharsets.UTF_8);
        BufferedWriter curOutput = Files.newBufferedWriter(new File("cards_" + System.currentTimeMillis()).toPath(), StandardCharsets.UTF_8);
        for (Map.Entry<String, List<String>> entry : all.entrySet()) {
            JsonCard ref = MtgJson.card(entry.getKey());
            if (ref == null) {
                System.out.println("skipping " + entry.getKey());
                continue;
            }
            refOutput.write("---\n");
            refOutput.write(entry.getKey());
            refOutput.write("\n");
            if (ref.text != null) {
                refOutput.write(ref.text.replace("Æ", "AE"));
                refOutput.write("\n");
            }
            curOutput.write("---\n");
            curOutput.write(entry.getKey());
            curOutput.write("\n");
            for (String line : entry.getValue()) {
                curOutput.write(line
                        .replace("&mdash;", "—")
                        .replace("<i>", "")
                        .replace("</i>", "")
                        .replace("<br>", "\n")
                        .replace("<br/>", "\n")
                        .replace("&bull;", "•")
                        .replace("{this}", entry.getKey())
                        .replace("{source}", entry.getKey()));
                curOutput.write("\n");
            }
        }
        refOutput.close();
        curOutput.close();
    }

    private static void addRules(SortedMap<String, List<String>> all, Card card) {
        if (!all.containsKey(card.getName())) {
            all.put(card.getName(), card.getRules());
        }
    }

}
