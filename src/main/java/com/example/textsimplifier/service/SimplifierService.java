package com.example.textsimplifier.service;

import com.example.textsimplifier.model.SimplifyRequest;
import com.example.textsimplifier.model.SimplifyResponse;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class SimplifierService {

    private static final List<String> FILLERS = Arrays.asList(
            "其实","实际上","总的来说","值得注意的是","可以看到","可以说","另外","与此同时",
            "当然","显然","的确","基本上","不过","就是说","换句话说","例如","比如"
    );

    private static final Set<String> STOP_CHARS = new HashSet<>(Arrays.asList(
            "的","了","是","在","和","我","有","与","就","也","为","不","人","中","上","他","她"
    ));

    // 分句：基于中文标点与换行
    private List<String> splitSentences(String text){
        if(text == null) return Collections.emptyList();
        String raw = text.replace("\r", "").trim();
        Pattern p = Pattern.compile("[^。！？；\\n]+[。！？；]?");
        Matcher m = p.matcher(raw);
        List<String> parts = new ArrayList<>();
        while(m.find()){
            String s = m.group().trim();
            if(!s.isEmpty()) parts.add(s);
        }
        if(parts.isEmpty()){
            for(String line : raw.split("\n")){
                line = line.trim();
                if(!line.isEmpty()) parts.add(line);
            }
        }
        return parts;
    }

    private String cleanText(String text, boolean cleanFillers){
        if(text == null) return "";
        String s = text.replaceAll("\\s+", " ");
        if(cleanFillers){
            for(String f : FILLERS){
                s = s.replace(f, "");
            }
        }
        s = s.replaceAll("([。！？；]){2,}", "$1");
        return s.trim();
    }

    private Map<Character, Integer> charVector(String s){
        Map<Character,Integer> vec = new HashMap<>();
        String clean = s.replaceAll("[，,。.。！？\\?!；;：:\\(\\)（）\\[\\]『』“”\"']", "")
                        .replaceAll("\\s+","");
        for(char c : clean.toCharArray()){
            vec.put(c, vec.getOrDefault(c,0)+1);
        }
        return vec;
    }

    private double cosSim(Map<Character,Integer> a, Map<Character,Integer> b){
        double dot = 0, na = 0, nb = 0;
        for(Map.Entry<Character,Integer> e : a.entrySet()){
            int av = e.getValue();
            na += av*av;
            int bv = b.getOrDefault(e.getKey(), 0);
            dot += av * bv;
        }
        for(int v : b.values()) nb += v*v;
        if(na == 0 || nb == 0) return 0.0;
        return dot / (Math.sqrt(na)*Math.sqrt(nb));
    }

    private boolean hasNumericHints(String s){
        return s.matches(".*[\\d０-９%].*") || s.contains("百分之") || s.matches(".*[年月日].*");
    }

    private Set<Character> topKeywords(List<String> sentences, int k){
        Map<Character,Integer> freq = new HashMap<>();
        for(String s : sentences){
            String txt = s.replaceAll("[，,。.。！？\\?!；;：:\\(\\)（）\\[\\]『』“”\"']", "")
                          .replaceAll("\\s+","");
            for(char c : txt.toCharArray()){
                if(Character.isDigit(c) || Character.isLetter(c)) continue;
                if(STOP_CHARS.contains(String.valueOf(c))) continue;
                freq.put(c, freq.getOrDefault(c,0)+1);
            }
        }
        return freq.entrySet().stream()
                .sorted((a,b)->b.getValue().compareTo(a.getValue()))
                .limit(k)
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }

    // 去重（返回保留的句子和被移除的句子）
    private DedupResult deduplicate(List<String> sentences, double threshold){
        List<Integer> keptIdx = new ArrayList<>();
        List<Integer> removedIdx = new ArrayList<>();
        List<Map<Character,Integer>> vecs = sentences.stream().map(this::charVector).collect(Collectors.toList());
        for(int i=0;i<sentences.size();i++){
            Map<Character,Integer> v = vecs.get(i);
            boolean isDup = false;
            for(int j : keptIdx){
                double sim = cosSim(v, vecs.get(j));
                if(sim >= threshold){
                    isDup = true; break;
                }
            }
            if(!isDup) keptIdx.add(i); else removedIdx.add(i);
        }
        List<String> kept = keptIdx.stream().map(sentences::get).collect(Collectors.toList());
        List<String> removed = removedIdx.stream().map(sentences::get).collect(Collectors.toList());
        return new DedupResult(kept, removed);
    }

    private static class DedupResult {
        public final List<String> kept;
        public final List<String> removed;
        public DedupResult(List<String> kept, List<String> removed){ this.kept=kept; this.removed=removed; }
    }

    public SimplifyResponse simplify(SimplifyRequest req){
        String cleaned = cleanText(req.text, req.cleanFillers);
        List<String> sents = splitSentences(cleaned);
        if(sents.isEmpty()){
            return new SimplifyResponse("", Collections.emptyList(), 0, 0);
        }
        List<String> pool = new ArrayList<>(sents);
        List<String> removedByDedup = Collections.emptyList();
        if(req.dedupe){
            DedupResult dr = deduplicate(pool, req.dupThreshold);
            pool = dr.kept;
            removedByDedup = dr.removed;
        }

        int origLen = cleaned.replaceAll("\\s+","").length();
        double keepRatio = (100.0 - req.compressRatio) / 100.0;
        int targetChars = Math.max(1, (int)Math.round(origLen * keepRatio));

        // 评分
        Set<Character> keywords = topKeywords(pool, 18);
        int total = pool.size();
        List<Score> scores = new ArrayList<>();
        for(int i=0;i<pool.size();i++){
            String s = pool.get(i);
            int score = 0;
            String txt = s.replaceAll("[，,。.。！？\\?!；;：:\\(\\)（）\\[\\]『』“”\"']", "")
                          .replaceAll("\\s+","");
            int keyHits = 0;
            for(char c : txt.toCharArray()) if(keywords.contains(c)) keyHits++;
            score += keyHits * 2;
            if(i < 2) score += 2;
            if(i >= total-2) score += 2;
            if(hasNumericHints(s)) score += 2;
            int len = txt.length();
            if(len < 6) score -= 1;
            if(len > 200) score -= 3;
            if(len > 80) score -= 1;
            int commas = (int) s.chars().filter(ch->ch=='，' || ch==',').count();
            score += Math.min(2, commas);
            scores.add(new Score(i, score));
        }
        scores.sort((a,b)->Integer.compare(b.score, a.score));

        Set<Integer> selected = new HashSet<>();
        int accumulated = 0;
        for(Score sc : scores){
            int idx = sc.idx;
            String sent = pool.get(idx);
            // 额外去重判断
            boolean dup = false;
            if(req.dedupe){
                Map<Character,Integer> v = charVector(sent);
                for(int si : selected){
                    Map<Character,Integer> v2 = charVector(pool.get(si));
                    if(cosSim(v, v2) >= req.dupThreshold) { dup = true; break; }
                }
            }
            if(dup) continue;
            selected.add(idx);
            accumulated += pool.get(idx).replaceAll("[，,。.。！？\\?!；;：:\\(\\)（）\\[\\]『』“”\"']", "").replaceAll("\\s+","").length();
            if(accumulated >= targetChars) break;
        }
        if(selected.isEmpty()) selected.add(0);

        List<Integer> finalIdxs = new ArrayList<>(selected);
        if(req.preserveOrder) Collections.sort(finalIdxs);
        else finalIdxs.sort((a,b) -> {
            int sa = scores.stream().filter(s->s.idx==a).findFirst().map(s->s.score).orElse(0);
            int sb = scores.stream().filter(s->s.idx==b).findFirst().map(s->s.score).orElse(0);
            return Integer.compare(sb, sa);
        });

        String finalText = finalIdxs.stream().map(pool::get).collect(Collectors.joining(" "));
        List<String> removed = new ArrayList<>();
        // 计算被移除（参考原 sentences sents）
        for(String s : sents){
            if(!finalText.contains(s)) removed.add(s);
        }
        // 合并由于去重引起的 removed
        removed.addAll(removedByDedup);
        int newLen = finalText.replaceAll("\\s+","").length();
        return new SimplifyResponse(finalText.trim(), removed, origLen, newLen);
    }

    private static class Score {
        int idx;
        int score;
        Score(int idx, int score){ this.idx=idx; this.score=score; }
    }
}