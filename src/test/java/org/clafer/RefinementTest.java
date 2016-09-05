package org.clafer;

import org.clafer.ast.AstAbstractClafer;
import org.clafer.ast.AstConcreteClafer;
import org.clafer.ast.AstModel;
import static org.clafer.ast.Asts.$this;
import static org.clafer.ast.Asts.equal;
import static org.clafer.ast.Asts.IntType;
import static org.clafer.ast.Asts.Mandatory;
import static org.clafer.ast.Asts.Many;
import static org.clafer.ast.Asts.add;
import static org.clafer.ast.Asts.constant;
import static org.clafer.ast.Asts.global;
import static org.clafer.ast.Asts.join;
import static org.clafer.ast.Asts.joinRef;
import static org.clafer.ast.Asts.newModel;
import org.clafer.compiler.ClaferCompiler;
import org.clafer.compiler.ClaferSolver;
import org.clafer.instance.InstanceClafer;
import org.clafer.instance.InstanceModel;
import org.clafer.scope.Scope;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 *
 * @author jimmy
 */
public class RefinementTest {

    /**
     * <pre>
     * abstract Animal
     *     abstract Limb
     *         Digit +
     *
     * Human : Animal 2
     *     Arm : Limb 2
     *     Leg : Limb 2
     * </pre>
     */
    @Test(timeout = 60000)
    public void testCardinalityRefinement() {
        AstModel model = newModel();

        AstAbstractClafer animal = model.addAbstract("Animal");
        AstAbstractClafer limb = animal.addAbstractChild("Limb");
        AstConcreteClafer digit = limb.addChild("Digit").withCard(Many);

        AstConcreteClafer human = model.addChild("Human").extending(animal).withCard(2, 2);
        AstConcreteClafer arm = human.addChild("Arm").extending(limb).withCard(2, 2);
        AstConcreteClafer leg = human.addChild("Leg").extending(limb).withCard(2, 2);

        ClaferSolver solver = ClaferCompiler.compile(model, Scope.defaultScope(8));
        while (solver.find()) {
            InstanceModel instance = solver.instance();
            InstanceClafer[] humanInstances = instance.getTopClafers(human);
            assertEquals(2, humanInstances.length);
            for (InstanceClafer humanInstance : humanInstances) {
                InstanceClafer[] armInstances = humanInstance.getChildren(arm);
                assertEquals(2, armInstances.length);
                for (InstanceClafer armInstance : armInstances) {
                    assertTrue(armInstance.getChildren(digit).length >= 1);
                }
                InstanceClafer[] legInstances = humanInstance.getChildren(leg);
                assertEquals(2, legInstances.length);
                for (InstanceClafer legtInstance : legInstances) {
                    assertTrue(legtInstance.getChildren(digit).length >= 1);
                }
            }
        }
        assertEquals(1, solver.instanceCount());
    }

    /**
     * <pre>
     * abstract Store
     *     abstract Item -> int
     *
     * Market : Store
     *     Flowers : Item
     *         [ this.dref = 3 ]
     *     Food : Item
     *         [ this.dref = 4 ]
     *
     * TotalCost -> int
     *     [ this.dref = Store.Item.dref + 1 ]
     * </pre>
     */
    @Test(timeout = 60000)
    public void testJoinOverRefinement() {
        AstModel model = newModel();

        AstAbstractClafer store = model.addAbstract("Store");
        AstAbstractClafer item = store.addAbstractChild("Item").refToUnique(IntType);

        AstConcreteClafer market = model.addChild("Market").extending(store).withCard(Mandatory);
        AstConcreteClafer flowers = market.addChild("Flowers").extending(item).withCard(Mandatory);
        flowers.addConstraint(equal(joinRef($this()), 3));
        AstConcreteClafer food = market.addChild("Food").extending(item).withCard(Mandatory);
        food.addConstraint(equal(joinRef($this()), 4));

        AstConcreteClafer totalCost = model.addChild("TotalCost").refToUnique(IntType).withCard(Mandatory);
        totalCost.addConstraint(equal(
                joinRef($this()),
                add(joinRef(join(global(store), item)), constant(1))));

        ClaferSolver solver = ClaferCompiler.compile(model, Scope.defaultScope(1));
        while (solver.find()) {
            InstanceModel instance = solver.instance();
            InstanceClafer marketInstance = instance.getTopClafer(market);
            InstanceClafer flowersInstance = marketInstance.getChild(flowers);
            assertEquals(3, flowersInstance.getRef());
            InstanceClafer foodInstance = marketInstance.getChild(food);
            assertEquals(4, foodInstance.getRef());
            InstanceClafer totalCostInstance = instance.getTopClafer(totalCost);
            assertEquals(8, totalCostInstance.getRef());
        }
        assertEquals(1, solver.instanceCount());
    }

    /**
     * <pre>
     * abstract Store
     *     abstract Item -> int
     *
     * Market : Store
     *     Flowers : Item
     *         [ this.dref = 3 ]
     *     Food : Item
     *         [ this.dref = 4 ]
     * BookStore : Store
     *     Book : Item
     *         [ this.dref = 5 ]
     *
     * TotalCost -> int
     *     [ this.dref = Store.Item.dref + 2 ]
     * MarketCost -> int
     *     [ this.dref = Market.Item.dref + 1 ]
     * BookStoreCost -> int
     *     [ this.dref = BookStore.Item.dref + 1 ]
     * </pre>
     */
    @Test(timeout = 60000)
    public void testJoinOverMultipleRefinement() {
        AstModel model = newModel();

        AstAbstractClafer store = model.addAbstract("Store");
        AstAbstractClafer item = store.addAbstractChild("Item").refToUnique(IntType);

        AstConcreteClafer market = model.addChild("Market").extending(store).withCard(Mandatory);
        AstConcreteClafer flowers = market.addChild("Flowers").extending(item).withCard(Mandatory);
        flowers.addConstraint(equal(joinRef($this()), 3));
        AstConcreteClafer food = market.addChild("Food").extending(item).withCard(Mandatory);
        food.addConstraint(equal(joinRef($this()), 4));
        AstConcreteClafer bookStore = model.addChild("BookStore").extending(store).withCard(Mandatory);
        AstConcreteClafer book = bookStore.addChild("Book").extending(item).withCard(Mandatory);
        book.addConstraint(equal(joinRef($this()), 5));

        AstConcreteClafer totalCost = model.addChild("TotalCost").refToUnique(IntType).withCard(Mandatory);
        totalCost.addConstraint(equal(
                joinRef($this()),
                add(joinRef(join(global(store), item)), constant(2))));
        AstConcreteClafer marketCost = model.addChild("MarketCost").refToUnique(IntType).withCard(Mandatory);
        marketCost.addConstraint(equal(
                joinRef($this()),
                add(joinRef(join(global(market), item)), constant(1))));
        AstConcreteClafer bookStoreCost = model.addChild("BookStoreCost").refToUnique(IntType).withCard(Mandatory);
        bookStoreCost.addConstraint(equal(
                joinRef($this()),
                add(joinRef(join(global(bookStore), item)), constant(1))));

        ClaferSolver solver = ClaferCompiler.compile(model, Scope.defaultScope(1));
        while (solver.find()) {
            InstanceModel instance = solver.instance();
            InstanceClafer marketInstance = instance.getTopClafer(market);
            InstanceClafer flowersInstance = marketInstance.getChild(flowers);
            assertEquals(3, flowersInstance.getRef());
            InstanceClafer foodInstance = marketInstance.getChild(food);
            assertEquals(4, foodInstance.getRef());
            InstanceClafer bookStoreInstance = instance.getTopClafer(bookStore);
            InstanceClafer bookInstance = bookStoreInstance.getChild(book);
            assertEquals(5, bookInstance.getRef());
            InstanceClafer totalCostInstance = instance.getTopClafer(totalCost);
            assertEquals(14, totalCostInstance.getRef());
            InstanceClafer marketCostInstance = instance.getTopClafer(marketCost);
            assertEquals(8, marketCostInstance.getRef());
            InstanceClafer bookStoreCostInstance = instance.getTopClafer(bookStoreCost);
            assertEquals(6, bookStoreCostInstance.getRef());
        }
        assertEquals(1, solver.instanceCount());
    }

    /**
     * <pre>
     * abstract Store
     *     abstract Item -> int
     *     abstract OnSaleItem : Item
     *
     * Market : Store
     *     Flowers : Item
     *         [ this.dref = 3 ]
     *     Food : OnSaleItem
     *         [ this.dref = 4 ]
     * BookStore : Store
     *     Book : Item
     *         [ this.dref = 5 ]
     *
     * TotalCost -> int
     *     [ this.dref = Store.Item.dref + 2 ]
     * OnSaleTotalCost -> int
     *     [ this.dref = Store.OnSaleItem.dref + 1 ]
     * MarketCost -> int
     *     [ this.dref = Market.Item.dref + 1 ]
     * BookStoreCost -> int
     *     [ this.dref = BookStore.Item.dref + 1 ]
     * </pre>
     */
    @Test(timeout = 60000)
    public void testJoinOverMultilevelRefinement() {
        AstModel model = newModel();

        AstAbstractClafer store = model.addAbstract("Store");
        AstAbstractClafer item = store.addAbstractChild("Item").refToUnique(IntType);
        AstAbstractClafer onSaleItem = store.addAbstractChild("OnSaleItemItem").extending(item);

        AstConcreteClafer market = model.addChild("Market").extending(store).withCard(Mandatory);
        AstConcreteClafer flowers = market.addChild("Flowers").extending(item).withCard(Mandatory);
        flowers.addConstraint(equal(joinRef($this()), 3));
        AstConcreteClafer food = market.addChild("Food").extending(onSaleItem).withCard(Mandatory);
        food.addConstraint(equal(joinRef($this()), 4));
        AstConcreteClafer bookStore = model.addChild("BookStore").extending(store).withCard(Mandatory);
        AstConcreteClafer book = bookStore.addChild("Book").extending(item).withCard(Mandatory);
        book.addConstraint(equal(joinRef($this()), 5));

        AstConcreteClafer totalCost = model.addChild("TotalCost").refToUnique(IntType).withCard(Mandatory);
        totalCost.addConstraint(equal(
                joinRef($this()),
                add(joinRef(join(global(store), item)), constant(2))));
        AstConcreteClafer onSaleTotalCost = model.addChild("OnSaleTotalCost").refToUnique(IntType).withCard(Mandatory);
        onSaleTotalCost.addConstraint(equal(
                joinRef($this()),
                add(joinRef(join(global(store), onSaleItem)), constant(1))));
        AstConcreteClafer marketCost = model.addChild("MarketCost").refToUnique(IntType).withCard(Mandatory);
        marketCost.addConstraint(equal(
                joinRef($this()),
                add(joinRef(join(global(market), item)), constant(1))));
        AstConcreteClafer bookStoreCost = model.addChild("BookStoreCost").refToUnique(IntType).withCard(Mandatory);
        bookStoreCost.addConstraint(equal(
                joinRef($this()),
                add(joinRef(join(global(bookStore), item)), constant(1))));

        ClaferSolver solver = ClaferCompiler.compile(model, Scope.defaultScope(1));
        while (solver.find()) {
            InstanceModel instance = solver.instance();
            InstanceClafer marketInstance = instance.getTopClafer(market);
            InstanceClafer flowersInstance = marketInstance.getChild(flowers);
            assertEquals(3, flowersInstance.getRef());
            InstanceClafer foodInstance = marketInstance.getChild(food);
            assertEquals(4, foodInstance.getRef());
            InstanceClafer bookStoreInstance = instance.getTopClafer(bookStore);
            InstanceClafer bookInstance = bookStoreInstance.getChild(book);
            assertEquals(5, bookInstance.getRef());
            InstanceClafer totalCostInstance = instance.getTopClafer(totalCost);
            assertEquals(14, totalCostInstance.getRef());
            InstanceClafer onSaleTotalCostInstance = instance.getTopClafer(onSaleTotalCost);
            assertEquals(5, onSaleTotalCostInstance.getRef());
            InstanceClafer marketCostInstance = instance.getTopClafer(marketCost);
            assertEquals(8, marketCostInstance.getRef());
            InstanceClafer bookStoreCostInstance = instance.getTopClafer(bookStoreCost);
            assertEquals(6, bookStoreCostInstance.getRef());
        }
        assertEquals(1, solver.instanceCount());
    }

    /**
     * <pre>
     * abstract Shape
     * Circle : Shape
     * Square : Shape
     *
     * abstract Sprite
     *     abstract Hitbox -> Shape
     *
     * Ball : Sprite
     *     BallHitbox : Hitbox -> Circle
     *
     * Box : Sprite
     *     BoxHitbox : Hitbox -> Square
     * </pre>
     */
    @Test(timeout = 60000)
    public void testReferenceRefinement() {
        AstModel model = newModel();

        AstAbstractClafer shape = model.addAbstract("Shape");
        AstConcreteClafer circle = model.addChild("Circle").extending(shape).withCard(Mandatory);
        AstConcreteClafer square = model.addChild("Square").extending(shape).withCard(Mandatory);

        AstAbstractClafer sprite = model.addAbstract("Sprite");
        AstAbstractClafer hitbox = sprite.addAbstractChild("Hitbox").refToUnique(shape);

        AstConcreteClafer ball = model.addChild("Ball").extending(sprite).withCard(Mandatory);
        AstConcreteClafer ballHitbox = ball.addChild("BallHitbox").extending(hitbox).refToUnique(circle).withCard(Mandatory);

        AstConcreteClafer box = model.addChild("Box").extending(sprite).withCard(Mandatory);
        AstConcreteClafer boxHitbox = box.addChild("BoxHitbox").extending(hitbox).refToUnique(square).withCard(Mandatory);

        ClaferSolver solver = ClaferCompiler.compile(model, Scope.defaultScope(8));
        while (solver.find()) {
            InstanceModel instance = solver.instance();
            InstanceClafer circleInstance = instance.getTopClafer(circle);
            InstanceClafer squareInstance = instance.getTopClafer(square);
            InstanceClafer ballInstance = instance.getTopClafer(ball);
            InstanceClafer ballHitboxInstance = ballInstance.getChild(ballHitbox);
            assertEquals(circleInstance, ballHitboxInstance.getRef());
            InstanceClafer boxInstance = instance.getTopClafer(box);
            InstanceClafer boxHitboxInstance = boxInstance.getChild(boxHitbox);
            assertEquals(squareInstance, boxHitboxInstance.getRef());
        }
        assertEquals(1, solver.instanceCount());
    }

    /**
     * <pre>
     * abstract Shape
     * Circle : Shape
     *     Radius -> int
     * Square : Shape
     *
     * abstract Sprite
     *     abstract Hitbox -> Shape
     *
     * Ball : Sprite
     *     BallHitbox : Hitbox -> Circle
     *     [ this.BallHitbox.dref.Radius.dref = 5 ]
     *
     * Box : Sprite
     *     BoxHitbox : Hitbox -> Square
     * </pre>
     */
    @Test(timeout = 60000)
    public void testJoinOverReferenceRefinement() {
        AstModel model = newModel();

        AstAbstractClafer shape = model.addAbstract("Shape");
        AstConcreteClafer circle = model.addChild("Circle").extending(shape).withCard(Mandatory);
        AstConcreteClafer radius = circle.addChild("Radius").refToUnique(IntType).withCard(Mandatory);
        AstConcreteClafer square = model.addChild("Square").extending(shape).withCard(Mandatory);

        AstAbstractClafer sprite = model.addAbstract("Sprite");
        AstAbstractClafer hitbox = sprite.addAbstractChild("Hitbox").refToUnique(shape);

        AstConcreteClafer ball = model.addChild("Ball").extending(sprite).withCard(Mandatory);
        AstConcreteClafer ballHitbox = ball.addChild("BallHitbox").extending(hitbox).refToUnique(circle).withCard(Mandatory);
        ball.addConstraint(equal(joinRef(join(joinRef(join($this(), ballHitbox)), radius)), 5));

        AstConcreteClafer box = model.addChild("Box").extending(sprite).withCard(Mandatory);
        AstConcreteClafer boxHitbox = box.addChild("BoxHitbox").extending(hitbox).refToUnique(square).withCard(Mandatory);

        ClaferSolver solver = ClaferCompiler.compile(model, Scope.defaultScope(8));
        while (solver.find()) {
            InstanceModel instance = solver.instance();
            InstanceClafer circleInstance = instance.getTopClafer(circle);
            InstanceClafer radiusInstance = circleInstance.getChild(radius);
            assertEquals(5, radiusInstance.getRef());
            InstanceClafer squareInstance = instance.getTopClafer(square);
            InstanceClafer ballInstance = instance.getTopClafer(ball);
            InstanceClafer ballHitboxInstance = ballInstance.getChild(ballHitbox);
            assertEquals(circleInstance, ballHitboxInstance.getRef());
            InstanceClafer boxInstance = instance.getTopClafer(box);
            InstanceClafer boxHitboxInstance = boxInstance.getChild(boxHitbox);
            assertEquals(squareInstance, boxHitboxInstance.getRef());
        }
        assertEquals(1, solver.instanceCount());
    }
}
