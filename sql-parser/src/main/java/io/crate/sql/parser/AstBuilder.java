/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.crate.sql.parser;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.Multimap;
import io.crate.sql.parser.antlr.v4.SqlBaseBaseVisitor;
import io.crate.sql.parser.antlr.v4.SqlBaseLexer;
import io.crate.sql.parser.antlr.v4.SqlBaseParser;
import io.crate.sql.tree.*;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.RuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static java.util.stream.Collectors.toList;

class AstBuilder extends SqlBaseBaseVisitor<Node> {
    private int parameterPosition = 1;

    @Override
    public Node visitSingleStatement(SqlBaseParser.SingleStatementContext context) {
        return visit(context.statement());
    }

    @Override
    public Node visitSingleExpression(SqlBaseParser.SingleExpressionContext context) {
        return visit(context.expr());
    }

    //  Statements

    @Override
    public Node visitBegin(SqlBaseParser.BeginContext context) {
        return new BeginStatement();
    }

    @Override
    public Node visitOptimize(SqlBaseParser.OptimizeContext context) {
        return new OptimizeStatement(
            visit(context.tableWithPartitions().tableWithPartition(), Table.class),
            visitIfPresent(context.withProperties(), GenericProperties.class).orElse(null));
    }

    @Override
    public Node visitCreateTable(SqlBaseParser.CreateTableContext context) {
        boolean notExists = context.EXISTS() != null;
        if (notExists) {
            return new CreateTable(
                (Table) visit(context.table()),
                visit(context.tableElement(), TableElement.class),
                visit(context.crateTableOption(), CrateTableOption.class),
                visitIfPresent(context.withProperties(), GenericProperties.class).orElse(null),
                true);
        }
        return new CreateTable(
            (Table) visit(context.table()),
            visit(context.tableElement(), TableElement.class),
            visit(context.crateTableOption(), CrateTableOption.class),
            visitIfPresent(context.withProperties(), GenericProperties.class).orElse(null),
            false);
    }

    @Override
    public Node visitCreateBlobTable(SqlBaseParser.CreateBlobTableContext context) {
        return new CreateBlobTable(
            (Table) visit(context.table()),
            visitIfPresent(context.numShards, ClusteredBy.class).orElse(null),
            visitIfPresent(context.withProperties(), GenericProperties.class).orElse(null));
    }

    @Override
    public Node visitCreateRepository(SqlBaseParser.CreateRepositoryContext context) {
        return new CreateRepository(context.name.getText(), context.type.getText(),
            visitIfPresent(context.withProperties(), GenericProperties.class).orElse(null));
    }

    @Override
    public Node visitCreateSnapshot(SqlBaseParser.CreateSnapshotContext context) {
        if (context.ALL() != null) {
            return new CreateSnapshot(getQualifiedName(context.qname()),
                visitIfPresent(context.withProperties(), GenericProperties.class).orElse(null));
        }
        return new CreateSnapshot(getQualifiedName(context.qname()),
            visit(context.tableWithPartitions().tableWithPartition(), Table.class),
            visitIfPresent(context.withProperties(), GenericProperties.class).orElse(null));
    }

    @Override
    public Node visitCreateAnalyzer(SqlBaseParser.CreateAnalyzerContext context) {
        return new CreateAnalyzer(
            context.name.getText(),
            null,
            visit(context.analyzerElement(), AnalyzerElement.class)
        );
    }

    @Override
    public Node visitCharFilters(SqlBaseParser.CharFiltersContext context) {
        return new CharFilters(visit(context.namedProperties(), NamedProperties.class));
    }

    @Override
    public Node visitTokenFilters(SqlBaseParser.TokenFiltersContext context) {
        return new TokenFilters(visit(context.namedProperties(), NamedProperties.class));
    }

    @Override
    public Node visitTokenizer(SqlBaseParser.TokenizerContext context) {
        return new Tokenizer((NamedProperties) visit(context.namedProperties()));
    }

    @Override
    public Node visitNamedProperties(SqlBaseParser.NamedPropertiesContext context) {
        return new NamedProperties(context.ident().getText(),
            visitIfPresent(context.withProperties(), GenericProperties.class).orElse(null));
    }

    @Override
    public Node visitRestore(SqlBaseParser.RestoreContext context) {
        if (context.ALL() != null) {
            return new RestoreSnapshot(getQualifiedName(context.qname()),
                visitIfPresent(context.withProperties(), GenericProperties.class).orElse(null));
        }
        return new RestoreSnapshot(getQualifiedName(context.qname()),
            visit(context.tableWithPartitions().tableWithPartition(), Table.class),
            visitIfPresent(context.withProperties(), GenericProperties.class).orElse(null));
    }

    @Override
    public Node visitShowCreateTable(SqlBaseParser.ShowCreateTableContext context) {
        return new ShowCreateTable((Table) visit(context.table()));
    }

    @Override
    public Node visitShowTransaction(SqlBaseParser.ShowTransactionContext contex) {
        return new ShowTransaction();
    }

    @Override
    public Node visitDropTable(SqlBaseParser.DropTableContext context) {
        return new DropTable((Table) visit(context.table()), context.EXISTS() != null);
    }

    @Override
    public Node visitDropRepository(SqlBaseParser.DropRepositoryContext context) {
        return new DropRepository(context.ident().getText());
    }

    @Override
    public Node visitDropBlobTable(SqlBaseParser.DropBlobTableContext context) {
        return new DropBlobTable((Table) visit(context.table()), context.EXISTS() != null);
    }

    @Override
    public Node visitDropAlias(SqlBaseParser.DropAliasContext context) {
        return new DropAlias(getQualifiedName(context.qname()));
    }

    @Override
    public Node visitDropSnapshot(SqlBaseParser.DropSnapshotContext context) {
        return new DropSnapshot(getQualifiedName(context.qname()));
    }

    @Override
    public Node visitCopyFrom(SqlBaseParser.CopyFromContext context) {
        return new CopyFrom(
            (Table) visit(context.tableWithPartition()),
            (Expression) visit(context.path),
            visitIfPresent(context.withProperties(), GenericProperties.class).orElse(null));
    }

    @Override
    public Node visitCopyTo(SqlBaseParser.CopyToContext context) {
        List<Expression> columns = Optional.ofNullable(context.columns())
            .map(list -> visit(list.primaryExpression(), Expression.class))
            .orElse(null);

        return new CopyTo(
            (Table) visit(context.tableWithPartition()),
            columns,
            visitIfPresent(context.where(), Expression.class).orElse(null),
            context.DIRECTORY() != null,
            (Expression) visit(context.path),
            visitIfPresent(context.withProperties(), GenericProperties.class).orElse(null));
    }

    @Override
    public Node visitInsert(SqlBaseParser.InsertContext context) {
        List<String> columns = context.ident()
            .stream()
            .map(RuleContext::getText)
            .collect(toList());

        List<Assignment> onDuplicateKeyAssignments = Optional.ofNullable(
            visit(context.assignment(), Assignment.class)).orElse(ImmutableList.of());

        if (context.insertSource().query() != null) {
            return new InsertFromSubquery(
                (Table) visit(context.table()),
                (Query) visit(context.insertSource().query()),
                columns,
                onDuplicateKeyAssignments);
        }

        return new InsertFromValues(
            (Table) visit(context.table()),
            visit(context.insertSource().values(), ValuesList.class),
            columns,
            onDuplicateKeyAssignments);
    }

    @Override
    public Node visitValues(SqlBaseParser.ValuesContext context) {
        return new ValuesList(visit(context.expr(), Expression.class));
    }

    @Override
    public Node visitDelete(SqlBaseParser.DeleteContext context) {
        return new Delete(
            (Relation) visit(context.aliasedRelation()),
            visitIfPresent(context.where(), Expression.class).orElse(null));
    }

    @Override
    public Node visitUpdate(SqlBaseParser.UpdateContext context) {
        return new Update(
            (Relation) visit(context.aliasedRelation()),
            visit(context.assignment(), Assignment.class),
            visitIfPresent(context.where(), Expression.class).orElse(null));
    }

    @Override
    public Node visitSet(SqlBaseParser.SetContext context) {
        Assignment setAssignment = prepareSetAssignment(context);
        if (context.LOCAL() != null) {
            return new SetStatement(SetStatement.Scope.LOCAL, setAssignment);
        } else {
            return new SetStatement(SetStatement.Scope.SESSION, setAssignment);
        }
    }

    private Assignment prepareSetAssignment(SqlBaseParser.SetContext context) {
        Expression settingName = new QualifiedNameReference(getQualifiedName(context.name));
        Assignment setAssignment;
        if (context.DEFAULT() != null) {
            setAssignment = new Assignment(settingName, ImmutableList.of());
        } else {
            setAssignment = new Assignment(settingName, visit(context.setExpr(), Expression.class));
        }
        return setAssignment;
    }

    @Override
    public Node visitSetGlobal(SqlBaseParser.SetGlobalContext context) {
        if (context.PERSISTENT() != null) {
            return new SetStatement(SetStatement.Scope.GLOBAL,
                SetStatement.SettingType.PERSISTENT,
                visit(context.setGlobalAssignment(), Assignment.class));
        } else {
            return new SetStatement(SetStatement.Scope.GLOBAL, visit(context.setGlobalAssignment(), Assignment.class));
        }
    }

    @Override
    public Node visitResetGlobal(SqlBaseParser.ResetGlobalContext context) {
        return new ResetStatement(visit(context.primaryExpression(), Expression.class));
    }

    @Override
    public Node visitKill(SqlBaseParser.KillContext context) {
        return new KillStatement((Expression) visit(context.jobId()));
    }

    @Override
    public Node visitKillAll(SqlBaseParser.KillAllContext context) {
        return new KillStatement();
    }

    @Override
    public Node visitExplain(SqlBaseParser.ExplainContext context) {
        return new Explain((Statement) visit(context.statement()), ImmutableList.of());
    }

    @Override
    public Node visitShowTables(SqlBaseParser.ShowTablesContext context) {
        return new ShowTables(
            Optional.ofNullable(context.qname()).map(AstBuilder::getQualifiedName).orElse(null),
            getTextIfPresent(context.pattern).map(AstBuilder::unquote).orElse(null),
            visitIfPresent(context.where(), Expression.class).orElse(null)
        );
    }

    @Override
    public Node visitShowSchemas(SqlBaseParser.ShowSchemasContext context) {
        return new ShowSchemas(
            getTextIfPresent(context.pattern).map(AstBuilder::unquote).orElse(null),
            visitIfPresent(context.where(), Expression.class).orElse(null)
        );
    }

    @Override
    public Node visitShowColumns(SqlBaseParser.ShowColumnsContext context) {
        QualifiedName schema = Optional.ofNullable(context.schema).map(AstBuilder::getQualifiedName).orElse(null);
        return new ShowColumns(
            getQualifiedName(context.tableName),
            schema,
            visitIfPresent(context.where(), Expression.class).orElse(null),
            getTextIfPresent(context.pattern).map(AstBuilder::unquote).orElse(null)
        );
    }

    @Override
    public Node visitRefreshTable(SqlBaseParser.RefreshTableContext context) {
        return new RefreshStatement(visit(context.tableWithPartitions().tableWithPartition(), Table.class));
    }

    @Override
    public Node visitTableOnly(SqlBaseParser.TableOnlyContext context) {
        return new Table(getQualifiedName(context.qname()));
    }

    @Override
    public Node visitTableWithPartition(SqlBaseParser.TableWithPartitionContext context) {
        return new Table(getQualifiedName(context.qname()), visit(context.assignment(), Assignment.class));
    }

    // Column / Table definition

    @Override
    public Node visitColumnDefinition(SqlBaseParser.ColumnDefinitionContext context) {
        return new ColumnDefinition(
            context.ident().getText(),
            visitIfPresent(context.generatedColumnDefinition(), Expression.class).orElse(null),
            visitIfPresent(context.dataType(), ColumnType.class).orElse(null),
            visit(context.columnConstraint(), ColumnConstraint.class));
    }

    @Override
    public Node visitColumnConstraintPrimaryKey(SqlBaseParser.ColumnConstraintPrimaryKeyContext context) {
        return new PrimaryKeyColumnConstraint();
    }

    @Override
    public Node visitColumnConstraintNotNull(SqlBaseParser.ColumnConstraintNotNullContext context) {
        return new NotNullColumnConstraint();
    }

    @Override
    public Node visitPrimaryKeyConstraint(SqlBaseParser.PrimaryKeyConstraintContext context) {
        return new PrimaryKeyConstraint(visit(context.columns().primaryExpression(), Expression.class));
    }

    @Override
    public Node visitColumnIndexOff(SqlBaseParser.ColumnIndexOffContext context) {
        return IndexColumnConstraint.OFF;
    }

    @Override
    public Node visitColumnIndexConstraint(SqlBaseParser.ColumnIndexConstraintContext context) {
        return new IndexColumnConstraint(
            context.method.getText(),
            visitIfPresent(context.withProperties(), GenericProperties.class).orElse(null));
    }

    @Override
    public Node visitIndexDefinition(SqlBaseParser.IndexDefinitionContext context) {
        return new IndexDefinition(
            context.name.getText(),
            context.method.getText(),
            visit(context.columns().primaryExpression(), Expression.class),
            visitIfPresent(context.withProperties(), GenericProperties.class).orElse(null));
    }

    @Override
    public Node visitPartitionedBy(SqlBaseParser.PartitionedByContext context) {
        return new PartitionedBy(visit(context.columns().primaryExpression(), Expression.class));
    }

    @Override
    public Node visitClusteredBy(SqlBaseParser.ClusteredByContext context) {
        return new ClusteredBy(
            visitIfPresent(context.routing, Expression.class).orElse(null),
            visitIfPresent(context.numShards, Expression.class).orElse(null)
        );
    }

    @Override
    public Node visitClusteredInto(SqlBaseParser.ClusteredIntoContext context) {
        return new ClusteredBy(null, visitIfPresent(context.numShards, Expression.class).orElse(null));
    }

    // Properties

    @Override
    public Node visitWithGenericProperties(SqlBaseParser.WithGenericPropertiesContext context) {
        return visitGenericProperties(context.genericProperties());
    }

    @Override
    public Node visitGenericProperties(SqlBaseParser.GenericPropertiesContext context) {
        GenericProperties properties = new GenericProperties();
        context.genericProperty().forEach(p -> properties.add((GenericProperty) visit(p)));
        return properties;
    }

    @Override
    public Node visitGenericProperty(SqlBaseParser.GenericPropertyContext context) {
        return new GenericProperty(context.ident().getText(), (Expression) visit(context.expr()));
    }

    // Amending tables

    @Override
    public Node visitAlterTableProperties(SqlBaseParser.AlterTablePropertiesContext context) {
        Table name = (Table) visit(context.alterTableDefinition());
        if (context.SET() != null) {
            return new AlterTable(name, (GenericProperties) visit(context.genericProperties()));
        }
        return new AlterTable(name, context.ident().stream().map(RuleContext::getText).collect(toList()));
    }

    @Override
    public Node visitAlterBlobTableProperties(SqlBaseParser.AlterBlobTablePropertiesContext context) {
        Table name = (Table) visit(context.alterTableDefinition());
        if (context.SET() != null) {
            return new AlterBlobTable(name, (GenericProperties) visit(context.genericProperties()));
        }
        return new AlterBlobTable(name, context.ident().stream().map(RuleContext::getText).collect(toList()));
    }

    @Override
    public Node visitAddColumnDefinition(SqlBaseParser.AddColumnDefinitionContext context) {
        return new AddColumnDefinition(
            (Expression) visit(context.subscriptSafe()),
            visitIfPresent(context.addGeneratedColumnDefinition(), Expression.class).orElse(null),
            visitIfPresent(context.dataType(), ColumnType.class).orElse(null),
            visit(context.columnConstraint(), ColumnConstraint.class));
    }

    @Override
    public Node visitAddColumn(SqlBaseParser.AddColumnContext context) {
        return new AlterTableAddColumn(
            (Table) visit(context.alterTableDefinition()),
            (AddColumnDefinition) visit(context.addColumnDefinition()));
    }

    // Assignments

    @Override
    public Node visitSetGlobalAssignment(SqlBaseParser.SetGlobalAssignmentContext context) {
        return new Assignment((Expression) visit(context.primaryExpression()), (Expression) visit(context.expr()));
    }

    @Override
    public Node visitAssignment(SqlBaseParser.AssignmentContext context) {
        return new Assignment((Expression) visit(context.primaryExpression()), (Expression) visit(context.expr()));
    }

    // Query specification

    @Override
    public Node visitQuery(SqlBaseParser.QueryContext context) {
        Query body = (Query) visit(context.queryNoWith());
        return new Query(
            wrapJavaOptional(visitIfPresent(context.with(), With.class)),
            body.getQueryBody(),
            body.getOrderBy(),
            body.getLimit(),
            body.getOffset()
        );
    }

    @Override
    public Node visitWith(SqlBaseParser.WithContext context) {
        return new With(context.RECURSIVE() != null, visit(context.namedQuery(), WithQuery.class));
    }

    @Override
    public Node visitNamedQuery(SqlBaseParser.NamedQueryContext context) {
        return new WithQuery(
            context.name.getText(), (Query) visit(context.query()),
            Optional.ofNullable(getColumnAliases(context.aliasedColumns())).orElse(ImmutableList.of())
        );
    }

    @Override
    public Node visitQueryNoWith(SqlBaseParser.QueryNoWithContext context) {
        QueryBody term = (QueryBody) visit(context.queryTerm());
        if (term instanceof QuerySpecification) {
            // When we have a simple query specification
            // followed by order by limit, fold the order by and limit
            // clauses into the query specification (analyzer/planner
            // expects this structure to resolve references with respect
            // to columns defined in the query specification)
            QuerySpecification query = (QuerySpecification) term;

            // TODO remove guava optionals
            return new Query(
                com.google.common.base.Optional.absent(),
                new QuerySpecification(
                    query.getSelect(),
                    query.getFrom(),
                    query.getWhere(),
                    query.getGroupBy(),
                    query.getHaving(),
                    visit(context.sortItem(), SortItem.class),
                    wrapJavaOptional(visitIfPresent(context.limit, Expression.class)),
                    wrapJavaOptional(visitIfPresent(context.offset, Expression.class))),
                ImmutableList.of(),
                com.google.common.base.Optional.absent(),
                com.google.common.base.Optional.absent());
        }

        return new Query(
            com.google.common.base.Optional.absent(),
            term,
            visit(context.sortItem(), SortItem.class),
            wrapJavaOptional(visitIfPresent(context.limit, Expression.class)),
            wrapJavaOptional(visitIfPresent(context.offset, Expression.class)));
    }

    @Override
    public Node visitQuerySpecification(SqlBaseParser.QuerySpecificationContext context) {
//        Optional<Relation> from = Optional.empty();
        List<SelectItem> selectItems = visit(context.selectItem(), SelectItem.class);

        List<Relation> relations = visit(context.relation(), Relation.class);
        if (!relations.isEmpty()) {
            // synthesize implicit join nodes
            Iterator<Relation> iterator = relations.iterator();
            Relation relation = iterator.next();
            // TODO fix fix
            while (iterator.hasNext()) {
//                relation = new Join(Join.Type.IMPLICIT, relation, iterator.next(), Optional.<JoinCriteria>empty());
            }

//            from = Optional.of(relation);
        } else {
            relations = null;
        }

        return new QuerySpecification(
            new Select(isDistinct(context.setQuant()), selectItems),
            relations,
            wrapJavaOptional(visitIfPresent(context.where(), Expression.class)),
            visit(context.expr(), Expression.class),
            wrapJavaOptional(visitIfPresent(context.having, Expression.class)),
            ImmutableList.of(),
            com.google.common.base.Optional.absent(),
            com.google.common.base.Optional.absent());
    }

    @Override
    public Node visitWhere(SqlBaseParser.WhereContext context) {
        return visit(context.condition);
    }

    @Override
    public Node visitSortItem(SqlBaseParser.SortItemContext context) {
        return new SortItem(
            (Expression) visit(context.expr()),
            Optional.ofNullable(context.ordering)
                .map(AstBuilder::getOrderingType)
                .orElse(SortItem.Ordering.ASCENDING),
            Optional.ofNullable(context.nullOrdering)
                .map(AstBuilder::getNullOrderingType)
                .orElse(SortItem.NullOrdering.UNDEFINED));
    }

    @Override
    public Node visitSetOperation(SqlBaseParser.SetOperationContext context) {
        QueryBody left = (QueryBody) visit(context.left);
        QueryBody right = (QueryBody) visit(context.right);

        boolean distinct = context.setQuant() == null || context.setQuant().DISTINCT() != null;

        switch (context.operator.getType()) {
            case SqlBaseLexer.UNION:
                return new Union(ImmutableList.of(left, right), distinct);
            case SqlBaseLexer.INTERSECT:
                return new Intersect(ImmutableList.of(left, right), distinct);
            case SqlBaseLexer.EXCEPT:
                return new Except(left, right, distinct);
        }

        throw new IllegalArgumentException("Unsupported set operation: " + context.operator.getText());
    }

    @Override
    public Node visitSelectAll(SqlBaseParser.SelectAllContext context) {
        if (context.qname() != null) {
            return new AllColumns(getQualifiedName(context.qname()));
        }

        return new AllColumns();
    }

    @Override
    public Node visitSelectSingle(SqlBaseParser.SelectSingleContext context) {
        // TODO cleanup
        com.google.common.base.Optional<String> alias = wrapJavaOptional(getTextIfPresent(context.ident()));
        return new SingleColumn((Expression) visit(context.expr()), alias);
    }

    @Override
    public Node visitUnquotedIdentifier(SqlBaseParser.UnquotedIdentifierContext context) {
        return new StringLiteral(context.IDENTIFIER().getText());
    }

    @Override
    public Node visitTable(SqlBaseParser.TableContext context) {
        QualifiedName name = getQualifiedName(context.qname());
        if (context.parameterOrLiteral() != null) {
            return new Table(name, visit(context.parameterOrLiteral(), Assignment.class));
        }
        return new Table(name);
    }

    @Override
    public Node visitSubquery(SqlBaseParser.SubqueryContext context) {
        return new TableSubquery((Query) visit(context.queryNoWith()));
    }

//    @Override
//    public Node visitInlineTable(SqlBaseParser.InlineTableContext context) {
//        return new Values(getLocation(context), visit(context.expression(), Expression.class));
//    }

    @Override
    public Node visitOver(SqlBaseParser.OverContext context) {
        return new Window(
            visit(context.expr(), Expression.class),
            visit(context.sortItem(), SortItem.class),
            visitIfPresent(context.windowFrame(), WindowFrame.class).orElse(null));
    }

    @Override
    public Node visitWindowFrame(SqlBaseParser.WindowFrameContext context) {
        return new WindowFrame(
            getFrameType(context.frameType),
            (FrameBound) visit(context.start),
            visitIfPresent(context.end, FrameBound.class).orElse(null));
    }

    @Override
    public Node visitUnboundedFrame(SqlBaseParser.UnboundedFrameContext context) {
        return new FrameBound(getUnboundedFrameBoundType(context.boundType));
    }

    @Override
    public Node visitBoundedFrame(SqlBaseParser.BoundedFrameContext context) {
        return new FrameBound(getBoundedFrameBoundType(context.boundType), (Expression) visit(context.expr()));
    }

    // Boolean expressions

    @Override
    public Node visitLogicalNot(SqlBaseParser.LogicalNotContext context) {
        return new NotExpression((Expression) visit(context.booleanExpression()));
    }

    @Override
    public Node visitLogicalBinary(SqlBaseParser.LogicalBinaryContext context) {
        return new LogicalBinaryExpression(
            getLogicalBinaryOperator(context.operator),
            (Expression) visit(context.left),
            (Expression) visit(context.right));
    }

    // From clause

    @Override
    public Node visitJoinRelation(SqlBaseParser.JoinRelationContext context) {
        Relation left = (Relation) visit(context.left);
        Relation right;

        if (context.CROSS() != null) {
            right = (Relation) visit(context.right);
            // TODO fix mismatch in optional types
            return new Join(Join.Type.CROSS, left, right, com.google.common.base.Optional.absent());
        }

        JoinCriteria criteria;
        if (context.NATURAL() != null) {
            right = (Relation) visit(context.right);
            criteria = new NaturalJoin();
        } else {
            right = (Relation) visit(context.rightRelation);
            if (context.joinCriteria().ON() != null) {
                criteria = new JoinOn((Expression) visit(context.joinCriteria().booleanExpression()));
            } else if (context.joinCriteria().USING() != null) {
                List<String> columns = context.joinCriteria()
                    .ident().stream()
                    .map(ParseTree::getText)
                    .collect(toList());

                criteria = new JoinUsing(columns);
            } else {
                throw new IllegalArgumentException("Unsupported join criteria");
            }
        }

        Join.Type joinType;
        if (context.joinType().LEFT() != null) {
            joinType = Join.Type.LEFT;
        } else if (context.joinType().RIGHT() != null) {
            joinType = Join.Type.RIGHT;
        } else if (context.joinType().FULL() != null) {
            joinType = Join.Type.FULL;
        } else {
            joinType = Join.Type.INNER;
        }
        // TODO fix mismatch in optional types
        return new Join(joinType, left, right, com.google.common.base.Optional.of(criteria));
    }

    @Override
    public Node visitAliasedRelation(SqlBaseParser.AliasedRelationContext context) {
        Relation child = (Relation) visit(context.relationPrimary());

        if (context.ident() == null) {
            return child;
        }

        return new AliasedRelation(child, context.ident().getText(), getColumnAliases(context.aliasedColumns()));
    }

//    @Override
//    public Node visitTableName(SqlBaseParser.TableNameContext context) {
//        if (context.table().)
//        return new Table(getQualifiedName(context.()));
//    }

    @Override
    public Node visitSubqueryRelation(SqlBaseParser.SubqueryRelationContext context) {
        return new TableSubquery((Query) visit(context.query()));
    }

    @Override
    public Node visitParenthesizedRelation(SqlBaseParser.ParenthesizedRelationContext context) {
        return visit(context.relation());
    }

    // Predicates

    @Override
    public Node visitPredicated(SqlBaseParser.PredicatedContext context) {
        if (context.predicate() != null) {
            return visit(context.predicate());
        }
        return visit(context.valueExpression);
    }

    @Override
    public Node visitComparison(SqlBaseParser.ComparisonContext context) {
        return new ComparisonExpression(
            getComparisonOperator(((TerminalNode) context.cmpOp().getChild(0)).getSymbol()),
            (Expression) visit(context.value),
            (Expression) visit(context.right));
    }

    @Override
    public Node visitDistinctFrom(SqlBaseParser.DistinctFromContext context) {
        Expression expression = new ComparisonExpression(
            ComparisonExpression.Type.IS_DISTINCT_FROM,
            (Expression) visit(context.value),
            (Expression) visit(context.right));

        if (context.NOT() != null) {
            expression = new NotExpression(expression);
        }
        return expression;
    }

    @Override
    public Node visitBetween(SqlBaseParser.BetweenContext context) {
        Expression expression = new BetweenPredicate(
            (Expression) visit(context.value),
            (Expression) visit(context.lower),
            (Expression) visit(context.upper));

        if (context.NOT() != null) {
            expression = new NotExpression(expression);
        }
        return expression;
    }

    @Override
    public Node visitNullPredicate(SqlBaseParser.NullPredicateContext context) {
        Expression child = (Expression) visit(context.value);

        if (context.NOT() == null) {
            return new IsNullPredicate(child);
        }
        return new IsNotNullPredicate(child);
    }

    @Override
    public Node visitLike(SqlBaseParser.LikeContext context) {
        Expression escape = null;
        if (context.escape != null) {
            escape = (Expression) visit(context.escape);
        }

        Expression result = new LikePredicate(
            (Expression) visit(context.value),
            (Expression) visit(context.pattern),
            escape);

        if (context.NOT() != null) {
            result = new NotExpression(result);
        }
        return result;
    }

    @Override
    public Node visitInList(SqlBaseParser.InListContext context) {
        Expression result = new InPredicate(
            (Expression) visit(context.value),
            new InListExpression(visit(context.expr(), Expression.class)));

        if (context.NOT() != null) {
            result = new NotExpression(result);
        }
        return result;
    }

    @Override
    public Node visitInSubquery(SqlBaseParser.InSubqueryContext context) {
        Expression result = new InPredicate(
            (Expression) visit(context.value),
            new SubqueryExpression((Query) visit(context.query())));

        if (context.NOT() != null) {
            result = new NotExpression(result);
        }
        return result;
    }

    @Override
    public Node visitExists(SqlBaseParser.ExistsContext context) {
        return new ExistsPredicate((Query) visit(context.query()));
    }

//    @Override
//    public Node visitQuantifiedComparison(SqlBaseParser.QuantifiedComparisonContext context) {
//        return new QuantifiedComparisonExpression(
//            getComparisonOperator(((TerminalNode) context.cmpOp().getChild(0)).getSymbol()),
//            getComparisonQuantifier(((TerminalNode) context.setCmpQuantifier().getChild(0)).getSymbol()),
//            (Expression) visit(context.value),
//            new SubqueryExpression((Query) visit(context.query())));
//    }

    // Value expressions

    @Override
    public Node visitArithmeticUnary(SqlBaseParser.ArithmeticUnaryContext context) {
        switch (context.operator.getType()) {
            case SqlBaseLexer.MINUS:
                return new NegativeExpression((Expression) visit(context.valueExpression()));
            case SqlBaseLexer.PLUS:
                return visit(context.valueExpression());
            default:
                throw new UnsupportedOperationException("Unsupported sign: " + context.operator.getText());
        }
    }

    @Override
    public Node visitArithmeticBinary(SqlBaseParser.ArithmeticBinaryContext context) {
        return new ArithmeticExpression(
            getArithmeticBinaryOperator(context.operator),
            (Expression) visit(context.left),
            (Expression) visit(context.right)
        );
    }

    @Override
    public Node visitConcatenation(SqlBaseParser.ConcatenationContext context) {
        return new FunctionCall(
            QualifiedName.of("concat"), ImmutableList.of(
            (Expression) visit(context.left),
            (Expression) visit(context.right)));
    }

    // Primary expressions

    @Override
    public Node visitCast(SqlBaseParser.CastContext context) {
        if (context.TRY_CAST() != null) {
            return new TryCast((Expression) visit(context.expr()), (ColumnType) visit(context.dataType()));
        } else {
            return new Cast((Expression) visit(context.expr()), (ColumnType) visit(context.dataType()));
        }
    }

    @Override
    public Node visitSpecialDateTimeFunction(SqlBaseParser.SpecialDateTimeFunctionContext context) {
        CurrentTime.Type type = getDateTimeFunctionType(context.name);
        if (context.precision != null) {
            return new CurrentTime(type, Integer.parseInt(context.precision.getText()));
        }

        return new CurrentTime(type);
    }

    @Override
    public Node visitExtract(SqlBaseParser.ExtractContext context) {
        return new Extract((Expression) visit(context.expr()), (Expression) visit(context.identExpr()));
    }

    @Override
    public Node visitSubstring(SqlBaseParser.SubstringContext context) {
        return new FunctionCall(QualifiedName.of("substr"), visit(context.expr(), Expression.class));
    }

    @Override
    public Node visitCurrentSchema(SqlBaseParser.CurrentSchemaContext context) {
        return new FunctionCall(QualifiedName.of("current_schema"), ImmutableList.of());

    }

    @Override
    public Node visitSubscript(SqlBaseParser.SubscriptContext context) {
        return new SubscriptExpression((Expression) visit(context.value), (Expression) visit(context.index));
    }

    @Override
    public Node visitNestedExpression(SqlBaseParser.NestedExpressionContext context) {
        return visit(context.expr());
    }

    //    @Override
//    public Node visitSubscriptSafe(SqlBaseParser.SubscriptSafeContext context) {
//        return new SubscriptExpression((Expression) visit(context.value), (Expression) visit(context.index));
//    }

    @Override
    public Node visitSubqueryExpression(SqlBaseParser.SubqueryExpressionContext context) {
        return new SubqueryExpression((Query) visit(context.query()));
    }

    @Override
    public Node visitDereference(SqlBaseParser.DereferenceContext context) {
        List<String> parts = context
            .ident().stream()
            .map(ParseTree::getText)
            .collect(toList());
        return new QualifiedNameReference(QualifiedName.of(parts));
    }

    @Override
    public Node visitColumnReference(SqlBaseParser.ColumnReferenceContext context) {
        return new QualifiedNameReference(QualifiedName.of(context.getText()));
    }

    @Override
    public Node visitSimpleCase(SqlBaseParser.SimpleCaseContext context) {
        return new SimpleCaseExpression(
            (Expression) visit(context.valueExpression()),
            visit(context.whenClause(), WhenClause.class),
            (Expression) visit(context.elseExpression));
    }

    @Override
    public Node visitSearchedCase(SqlBaseParser.SearchedCaseContext context) {
        return new SearchedCaseExpression(
            visit(context.whenClause(), WhenClause.class),
            (Expression) visit(context.elseExpression));
    }

    @Override
    public Node visitWhenClause(SqlBaseParser.WhenClauseContext context) {
        return new WhenClause((Expression) visit(context.condition), (Expression) visit(context.result));
    }

    @Override
    public Node visitFunctionCall(SqlBaseParser.FunctionCallContext context) {
        return new FunctionCall(
            getQualifiedName(context.qname()),
            visitIfPresent(context.over(), Window.class).orElse(null),
            isDistinct(context.setQuant()),
            visit(context.expr(), Expression.class));
    }

    // Literals

    @Override
    public Node visitNullLiteral(SqlBaseParser.NullLiteralContext context) {
        return NullLiteral.INSTANCE;
    }

    @Override
    public Node visitStringLiteral(SqlBaseParser.StringLiteralContext context) {
        return new StringLiteral(unquote(context.STRING().getText()));
    }

    @Override
    public Node visitIntegerLiteral(SqlBaseParser.IntegerLiteralContext context) {
        return new LongLiteral(context.getText());
    }

    @Override
    public Node visitDecimalLiteral(SqlBaseParser.DecimalLiteralContext context) {
        return new DoubleLiteral(context.getText());
    }

    @Override
    public Node visitBooleanLiteral(SqlBaseParser.BooleanLiteralContext context) {
        return Literal.fromObject(Boolean.valueOf(context.getText()));
    }

    @Override
    public Node visitArrayLiteral(SqlBaseParser.ArrayLiteralContext context) {
        return new ArrayLiteral(visit(context.expr(), Expression.class));
    }

    @Override
    public Node visitDateLiteral(SqlBaseParser.DateLiteralContext context) {
        return new DateLiteral(context.STRING().getText());
    }

    @Override
    public Node visitTimeLiteral(SqlBaseParser.TimeLiteralContext context) {
        return new TimeLiteral(context.STRING().getText());
    }

    @Override
    public Node visitTimestampLiteral(SqlBaseParser.TimestampLiteralContext context) {
        return new TimestampLiteral(context.STRING().getText());
    }

    @Override
    public Node visitObjectLiteral(SqlBaseParser.ObjectLiteralContext context) {
        Multimap<String, Expression> objAttributes = LinkedListMultimap.create();
        context.objectKeyValue().forEach(attr ->
            objAttributes.put(attr.key.getText(), (Expression) visit(attr.value))
        );
        return new ObjectLiteral(objAttributes);
    }

    @Override
    public Node visitOn(SqlBaseParser.OnContext context) {
        return BooleanLiteral.TRUE_LITERAL;
    }

    @Override
    public Node visitDataType(SqlBaseParser.DataTypeContext context) {
        if (context.objectTypeDefinition() != null) {
            return new ObjectColumnType(
                getObjectType(context.objectTypeDefinition().type),
                visit(context.objectTypeDefinition().columnDefinition(), ColumnDefinition.class));
        } else if (context.arrayTypeDefinition() != null) {
            CollectionColumnType.array((ColumnType) visit(context.setTypeDefinition().dataType()));
        } else if (context.setTypeDefinition() != null) {
            CollectionColumnType.set((ColumnType) visit(context.setTypeDefinition().dataType()));
        }
        return new ColumnType(context.getText().toLowerCase(Locale.ENGLISH));
    }

    private String getObjectType(Token type) {
        if (type == null) return null;
        switch (type.getType()) {
            case SqlBaseLexer.DYNAMIC:
                return type.getText().toLowerCase(Locale.ENGLISH);
            case SqlBaseLexer.STRICT:
                return type.getText().toLowerCase(Locale.ENGLISH);
            case SqlBaseLexer.IGNORED:
                return type.getText().toLowerCase(Locale.ENGLISH);
        }

        throw new UnsupportedOperationException("Unsupported object type: " + type.getText());
    }

    @Override
    public Node visitParameterPlaceholder(SqlBaseParser.ParameterPlaceholderContext context) {
        return new ParameterExpression(parameterPosition++);
    }

    @Override
    public Node visitPositionalParameter(SqlBaseParser.PositionalParameterContext context) {
        return new ParameterExpression(Integer.valueOf(context.integerLiteral().getText()));
    }

    // Helpers

    @Override
    protected Node defaultResult() {
        return null;
    }

    @Override
    protected Node aggregateResult(Node aggregate, Node nextResult) {
        if (nextResult == null) {
            throw new UnsupportedOperationException("not yet implemented");
        }
        if (aggregate == null) {
            return nextResult;
        }

        throw new UnsupportedOperationException("not yet implemented");
    }

    private <T> Optional<T> visitIfPresent(ParserRuleContext context, Class<T> clazz) {
        return Optional.ofNullable(context)
            .map(this::visit)
            .map(clazz::cast);
    }

    private <T> List<T> visit(List<? extends ParserRuleContext> contexts, Class<T> clazz) {
        return contexts.stream()
            .map(this::visit)
            .map(clazz::cast)
            .collect(toList());
    }

    private static String unquote(String value) {
        return value.substring(1, value.length() - 1)
            .replace("''", "'");
    }

    private static QualifiedName getQualifiedName(SqlBaseParser.QnameContext context) {
        List<String> parts = context
            .ident().stream()
            .map(ParseTree::getText)
            .collect(toList());

        return QualifiedName.of(parts);
    }

    private static boolean isDistinct(SqlBaseParser.SetQuantContext setQuantifier) {
        return setQuantifier != null && setQuantifier.DISTINCT() != null;
    }

    private static Optional<String> getTextIfPresent(ParserRuleContext context) {
        return Optional.ofNullable(context).map(ParseTree::getText);
    }

    private static Optional<String> getTextIfPresent(Token token) {
        return Optional.ofNullable(token).map(Token::getText);
    }

    private static List<String> getColumnAliases(SqlBaseParser.AliasedColumnsContext columnAliasesContext) {
        if (columnAliasesContext == null) {
            return null;
        }
        return columnAliasesContext
            .ident().stream()
            .map(ParseTree::getText)
            .collect(toList());
    }

    private static ArithmeticExpression.Type getArithmeticBinaryOperator(Token operator) {
        switch (operator.getType()) {
            case SqlBaseLexer.PLUS:
                return ArithmeticExpression.Type.ADD;
            case SqlBaseLexer.MINUS:
                return ArithmeticExpression.Type.SUBTRACT;
            case SqlBaseLexer.ASTERISK:
                return ArithmeticExpression.Type.MULTIPLY;
            case SqlBaseLexer.SLASH:
                return ArithmeticExpression.Type.DIVIDE;
            case SqlBaseLexer.PERCENT:
                return ArithmeticExpression.Type.MODULUS;
        }

        throw new UnsupportedOperationException("Unsupported operator: " + operator.getText());
    }

    private static ComparisonExpression.Type getComparisonOperator(Token symbol) {
        switch (symbol.getType()) {
            case SqlBaseLexer.EQ:
                return ComparisonExpression.Type.EQUAL;
            case SqlBaseLexer.NEQ:
                return ComparisonExpression.Type.NOT_EQUAL;
            case SqlBaseLexer.LT:
                return ComparisonExpression.Type.LESS_THAN;
            case SqlBaseLexer.LTE:
                return ComparisonExpression.Type.LESS_THAN_OR_EQUAL;
            case SqlBaseLexer.GT:
                return ComparisonExpression.Type.GREATER_THAN;
//            case SqlBaseLexer.IS_DISTINCT_FROM:
//                return ComparisonExpression.Type.IS_DISTINCT_FROM; TODO
            case SqlBaseLexer.REGEX_MATCH:
                return ComparisonExpression.Type.REGEX_MATCH;
            case SqlBaseLexer.REGEX_NO_MATCH:
                return ComparisonExpression.Type.REGEX_NO_MATCH;
            case SqlBaseLexer.REGEX_MATCH_CI:
                return ComparisonExpression.Type.REGEX_MATCH_CI;
            case SqlBaseLexer.REGEX_NO_MATCH_CI:
                return ComparisonExpression.Type.REGEX_NO_MATCH_CI;
        }

        throw new IllegalArgumentException("Unsupported operator: " + symbol.getText());
    }

    private static CurrentTime.Type getDateTimeFunctionType(Token token) {
        switch (token.getType()) {
            case SqlBaseLexer.CURRENT_DATE:
                return CurrentTime.Type.DATE;
            case SqlBaseLexer.CURRENT_TIME:
                return CurrentTime.Type.TIME;
            case SqlBaseLexer.CURRENT_TIMESTAMP:
                return CurrentTime.Type.TIMESTAMP;
        }

        throw new IllegalArgumentException("Unsupported special function: " + token.getText());
    }

    private static LogicalBinaryExpression.Type getLogicalBinaryOperator(Token token) {
        switch (token.getType()) {
            case SqlBaseLexer.AND:
                return LogicalBinaryExpression.Type.AND;
            case SqlBaseLexer.OR:
                return LogicalBinaryExpression.Type.OR;
        }

        throw new IllegalArgumentException("Unsupported operator: " + token.getText());
    }

    private static SortItem.NullOrdering getNullOrderingType(Token token) {
        switch (token.getType()) {
            case SqlBaseLexer.FIRST:
                return SortItem.NullOrdering.FIRST;
            case SqlBaseLexer.LAST:
                return SortItem.NullOrdering.LAST;
        }

        throw new IllegalArgumentException("Unsupported ordering: " + token.getText());
    }

    private static SortItem.Ordering getOrderingType(Token token) {
        switch (token.getType()) {
            case SqlBaseLexer.ASC:
                return SortItem.Ordering.ASCENDING;
            case SqlBaseLexer.DESC:
                return SortItem.Ordering.DESCENDING;
        }

        throw new IllegalArgumentException("Unsupported ordering: " + token.getText());
    }

    private static ArrayComparisonExpression.Quantifier getComparisonQuantifier(Token symbol) {
        switch (symbol.getType()) {
            case SqlBaseLexer.ALL:
                return ArrayComparisonExpression.Quantifier.ALL;
            case SqlBaseLexer.ANY:
                return ArrayComparisonExpression.Quantifier.ANY;
            case SqlBaseLexer.SOME:
                return ArrayComparisonExpression.Quantifier.ANY;
        }

        throw new IllegalArgumentException("Unsupported quantifier: " + symbol.getText());
    }

    @Override
    public Node visitSampledRelation(SqlBaseParser.SampledRelationContext context) {
        Relation child = (Relation) visit(context.aliasedRelation());

        if (context.TABLESAMPLE() == null) {
            return child;
        }

        return new SampledRelation(
            child,
            getSamplingMethod((Token) context.sampleType().getChild(0).getPayload()),
            (Expression) visit(context.percentage),
            com.google.common.base.Optional.absent() // TODO fix mismatch in optional types
        );
    }

    private static WindowFrame.Type getFrameType(Token type) {
        switch (type.getType()) {
            case SqlBaseLexer.RANGE:
                return WindowFrame.Type.RANGE;
            case SqlBaseLexer.ROWS:
                return WindowFrame.Type.ROWS;
        }

        throw new IllegalArgumentException("Unsupported frame type: " + type.getText());
    }

    private static FrameBound.Type getBoundedFrameBoundType(Token token) {
        switch (token.getType()) {
            case SqlBaseLexer.PRECEDING:
                return FrameBound.Type.PRECEDING;
            case SqlBaseLexer.FOLLOWING:
                return FrameBound.Type.FOLLOWING;
        }

        throw new IllegalArgumentException("Unsupported bound type: " + token.getText());
    }

    private static FrameBound.Type getUnboundedFrameBoundType(Token token) {
        switch (token.getType()) {
            case SqlBaseLexer.PRECEDING:
                return FrameBound.Type.UNBOUNDED_PRECEDING;
            case SqlBaseLexer.FOLLOWING:
                return FrameBound.Type.UNBOUNDED_FOLLOWING;
        }

        throw new IllegalArgumentException("Unsupported bound type: " + token.getText());
    }

    private static SampledRelation.Type getSamplingMethod(Token token) {
        switch (token.getType()) {
            case SqlBaseLexer.BERNOULLI:
                return SampledRelation.Type.BERNOULLI;
            case SqlBaseLexer.SYSTEM:
                return SampledRelation.Type.SYSTEM;
        }

        throw new IllegalArgumentException("Unsupported sampling method: " + token.getText());
    }

    // TODO Remove this method once the statements use java optional
    private <T> com.google.common.base.Optional<T> wrapJavaOptional(Optional<T> optional) {
        return com.google.common.base.Optional.fromNullable(optional.orElse(null));
    }
}
