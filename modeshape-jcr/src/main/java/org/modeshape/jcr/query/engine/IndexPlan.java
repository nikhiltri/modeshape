/*
 * ModeShape (http://www.modeshape.org)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.modeshape.jcr.query.engine;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import javax.jcr.query.qom.Constraint;
import javax.jcr.query.qom.JoinCondition;
import org.modeshape.common.annotation.Immutable;
import org.modeshape.common.util.CheckArg;
import org.modeshape.jcr.spi.index.provider.IndexProvider;

@Immutable
public final class IndexPlan implements Comparable<IndexPlan> {

    private static final Map<String, Object> NO_PARAMETERS = Collections.emptyMap();

    private final String name;
    private final String workspaceName;
    private final String providerName;
    private final int costEstimate;
    private final long cardinalityEstimate;
    private final Float selectivityEstimate;
    private final Collection<Constraint> constraints;
    private final Collection<JoinCondition> joinConditions;
    private final Map<String, Object> parameters;

    public IndexPlan( String name,
                      String workspaceName,
                      String providerName,
                      Collection<Constraint> constraints,
                      Collection<JoinCondition> joinConditions,
                      int costEstimate,
                      long cardinalityEstimate,
                      float selectivityEstimate,
                      Map<String, Object> parameters ) {
        CheckArg.isNotEmpty(name, "name");
        CheckArg.isNonNegative(costEstimate, "costEstimate");
        CheckArg.isNonNegative(cardinalityEstimate, "cardinalityEstimate");
        this.name = name;
        this.workspaceName = workspaceName;
        this.providerName = providerName; // may be null or empty
        this.constraints = constraints != null ? constraints : Collections.<Constraint>emptyList();
        this.joinConditions = joinConditions != null ? joinConditions : Collections.<JoinCondition>emptyList();
        this.costEstimate = costEstimate;
        this.cardinalityEstimate = cardinalityEstimate;
        this.selectivityEstimate = selectivityEstimate < 0 ? null : selectivityEstimate;
        this.parameters = parameters == null ? NO_PARAMETERS : parameters;
    }

    /**
     * Return an esimate of the number of nodes that will be returned by this index given the constraints. For example, an index
     * that will return one node should have a cardinality of 1.
     * <p>
     * When possible, the actual cardinality should be used. However, since an accurate number is often expensive or impossible to
     * determine in the planning phase, the cardinality can instead represent a rough order of magnitude.
     * </p>
     * <p>
     * Indexes with lower costs and lower {@link #getCardinalityEstimate() cardinalities} will be favored over other indexes.
     * </p>
     *
     * @return the cardinality estimate; never negative
     */
    public long getCardinalityEstimate() {
        return cardinalityEstimate;
    }

    /**
     * Return an estimate of the cost of using the index for the query in question. An index that is expensive to use will have a
     * higher cost than another index that is less expensive to use. For example, if a {@link IndexProvider} that owns the index
     * is in a remote process, then the cost estimate will need to take into account the cost of transmitting the request with the
     * criteria and the response with all of the node that meet the criteria of the index.
     * <p>
     * Indexes with lower costs and lower {@link #getCardinalityEstimate() cardinalities} will be favored over other indexes.
     * </p>
     *
     * @return the cost estimate; never negative
     */
    public int getCostEstimate() {
        return costEstimate;
    }

    /**
     * Determine if there is a {@link #getSelectivityEstimate() selectivity estimate}. This is equivalent to calling:
     *
     * <pre>
     *   return getSelectivityEstimate() != null
     * </pre>
     *
     * @return true if there is an estimate of selectivity, or false if there is none
     */
    public boolean hasSelectivityEstimate() {
        return selectivityEstimate != null;
    }

    /**
     * Get the estimate of the selectivity of this index for the query constraints. The selectivity is the ration of the number of
     * rows that will be returned to the total number of rows, or
     *
     * <pre>
     * selectivity = cardinality / total
     * </pre>
     *
     * Thus the selectivity (if known) will always be between 0 and 1.0, inclusive.
     * <p>
     * This method returns the estimated selectivity if it is know, or null if it is not known.
     *
     * @return the selectivity estimate, or null if there is none
     */
    public Float getSelectivityEstimate() {
        return selectivityEstimate;
    }

    /**
     * Get the name of this index.
     *
     * @return the index name; never null
     */
    public String getName() {
        return name;
    }

    /**
     * Get the name of the workspace to which this index applies.
     *
     * @return the workspace name; may be null if an implicit workspace is used
     */
    public String getWorkspaceName() {
        return workspaceName;
    }

    /**
     * The name of the provider that owns the index.
     *
     * @return the provider name; null if the index is handled internally by ModeShape by something other than a provider
     */
    public String getProviderName() {
        return providerName;
    }

    /**
     * Get the constraints that should be applied to this index if/when it is used.
     *
     * @return the constraints; may be null or empty if there are no constraints
     */
    public Collection<Constraint> getConstraints() {
        return constraints;
    }

    /**
     * Get the join conditions that should be applied to this index if/when it is used.
     *
     * @return the join conditions; may be null or empty if there are no join conditions
     */
    public Collection<JoinCondition> getJoinConditions() {
        return joinConditions;
    }

    /**
     * Get the provider-specific parameters for this index usage.
     *
     * @return the parameters; never null but possibly empty
     */
    public Map<String, Object> getParameters() {
        return parameters;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(getName());
        sb.append(" cost=").append(getCostEstimate());
        sb.append(", cardinality=").append(getCardinalityEstimate());
        for (Map.Entry<String, Object> entry : parameters.entrySet()) {
            sb.append(", ").append(entry.getKey()).append("=").append(entry.getValue());
        }
        return sb.toString();
    }

    @Override
    public int compareTo( IndexPlan that ) {
        if (that == this) return 0;
        if (that == null) return 1;
        // First, any index whose cardinality is >0 is better than <=0 ...
        if (this.getCardinalityEstimate() <= 0L) {
            if (that.getCardinalityEstimate() != 0L) {
                // 'that' is better (lower), so return positive 1 ...
                return 1;
            }
        } else if (that.getCardinalityEstimate() <= 0L) {
            // 'this' is better (lower), so return negative ...
            assert this.getCardinalityEstimate() > 0L;
            return -1;
        }
        // Then base it upon the cost ...
        return this.getCostEstimate() - that.costEstimate;
    }
}
