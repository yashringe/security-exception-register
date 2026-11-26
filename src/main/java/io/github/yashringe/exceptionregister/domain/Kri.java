package io.github.yashringe.exceptionregister.domain;

/**
 * One key risk indicator with its traffic-light status.
 *
 * @param id        stable machine name, used in the CSV export and by dashboards
 * @param name      what a manager reads
 * @param value     current value
 * @param unit      "count", "percent" or "days"
 * @param amberFrom value at which the indicator turns amber
 * @param redFrom   value at which it turns red
 */
public record Kri(String id, String name, double value, String unit, double amberFrom, double redFrom) {

    public enum Status { GREEN, AMBER, RED }

    /** All indicators here are "higher is worse". */
    public Status status() {
        if (value >= redFrom) return Status.RED;
        if (value >= amberFrom) return Status.AMBER;
        return Status.GREEN;
    }
}
