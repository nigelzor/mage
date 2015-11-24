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
import java.util.regex.Pattern;

public class CompareCardText {

    public static void main(String[] args) throws IOException {
        SortedMap<String, List<String>> all = new TreeMap<>();
        for (Card card : CompareWithMtgjsonTest.allCards()) {
            if (card.isSplitCard()) {
                addRules(all, ((SplitCard) card).getLeftHalfCard());
                addRules(all, ((SplitCard) card).getRightHalfCard());
            } else {
                addRules(all, card);
            }
        }

        Pattern cardname = Pattern.compile("\\{(?:this|source)\\}");
        Pattern remove = Pattern.compile("</?i>");

        BufferedWriter refOutput = Files.newBufferedWriter(new File("cards_reference").toPath(), StandardCharsets.UTF_8);
        BufferedWriter curOutput = Files.newBufferedWriter(new File("cards_" + System.currentTimeMillis()).toPath(), StandardCharsets.UTF_8);
        for (Map.Entry<String, List<String>> entry : all.entrySet()) {
            JsonCard ref = MtgJson.find(entry.getKey());
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
                line = remove.matcher(line).replaceAll("");
                line = cardname.matcher(line).replaceAll(entry.getKey());
                curOutput.write(line);
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
