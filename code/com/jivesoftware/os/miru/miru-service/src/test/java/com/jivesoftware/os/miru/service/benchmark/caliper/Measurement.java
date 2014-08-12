package com.jivesoftware.os.miru.service.benchmark.caliper;

import com.google.common.base.Function;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.Multimaps;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Taken from Google Caliper so we can upload our own results
 */
public class Measurement {
    public static ImmutableListMultimap<String, Measurement> indexByDescription(
        Iterable<Measurement> measurements) {
        return Multimaps.index(measurements, new Function<Measurement, String>() {
            @Override
            public String apply(Measurement input) {
                return input.description;
            }
        });
    }

    private Value value;
    private double weight;
    private String description;

    private Measurement() {
        this.value = Value.DEFAULT;
        this.weight = 0.0;
        this.description = "";
    }

    private Measurement(Builder builder) {
        this.value = builder.value;
        this.description = builder.description;
        this.weight = builder.weight;
    }

    @Override public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        } else if (obj instanceof Measurement) {
            Measurement that = (Measurement) obj;
            return this.value.equals(that.value)
                && this.weight == that.weight
                && this.description.equals(that.description);
        } else {
            return false;
        }
    }

    @Override public int hashCode() {
        return Objects.hashCode(value, weight, description);
    }

    @Override public String toString() {
        return Objects.toStringHelper(this)
            .add("value", value)
            .add("weight", weight)
            .add("description", description)
            .toString();
    }

    public Value value() {
        return value;
    }

    public double weight() {
        return weight;
    }

    public String description() {
        return description;
    }

    public static final class Builder {
        private Value value;
        private Double weight;
        private String description;

        public Builder value(Value value) {
            this.value = checkNotNull(value);
            return this;
        }

        public Builder weight(double weight) {
            checkArgument(weight > 0);
            this.weight = weight;
            return this;
        }

        public Builder description(String description) {
            this.description = checkNotNull(description);
            return this;
        }

        public Measurement build() {
            checkArgument(value != null);
            checkArgument(weight != null);
            checkArgument(description != null);
            return new Measurement(this);
        }
    }
}