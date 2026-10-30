package io.micronaut.serde.oracle.jdbc.json;

import io.micronaut.serde.annotation.Serdeable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Serdeable
public class OsonNested {
    private String name;
    private boolean flag;
    private BigDecimal ratio;
    private List<Integer> scores;
    private OsonInner inner;
    private Map<String, Object> extra;
    private List<OsonInner> inners;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isFlag() {
        return flag;
    }

    public void setFlag(boolean flag) {
        this.flag = flag;
    }

    public BigDecimal getRatio() {
        return ratio;
    }

    public void setRatio(BigDecimal ratio) {
        this.ratio = ratio;
    }

    public List<Integer> getScores() {
        return scores;
    }

    public void setScores(List<Integer> scores) {
        this.scores = scores;
    }

    public OsonInner getInner() {
        return inner;
    }

    public void setInner(OsonInner inner) {
        this.inner = inner;
    }

    public Map<String, Object> getExtra() {
        return extra;
    }

    public void setExtra(Map<String, Object> extra) {
        this.extra = extra;
    }

    public List<OsonInner> getInners() {
        return inners;
    }

    public void setInners(List<OsonInner> inners) {
        this.inners = inners;
    }
}
