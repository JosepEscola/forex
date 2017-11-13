package forex.strategy;

import com.dukascopy.api.Configurable;
import com.dukascopy.api.Filter;
import com.dukascopy.api.IAccount;
import com.dukascopy.api.IBar;
import com.dukascopy.api.IConsole;
import com.dukascopy.api.IContext;
import com.dukascopy.api.IEngine;
import com.dukascopy.api.IEngine.OrderCommand;
import com.dukascopy.api.IHistory;
import com.dukascopy.api.IIndicators;
import com.dukascopy.api.IIndicators.AppliedPrice;
import com.dukascopy.api.IMessage;
import com.dukascopy.api.IOrder;
import com.dukascopy.api.IStrategy;
import com.dukascopy.api.ITick;
import com.dukascopy.api.Instrument;
import com.dukascopy.api.JFException;
import com.dukascopy.api.OfferSide;
import com.dukascopy.api.Period;
import com.dukascopy.api.feed.IFeedDescriptor;
import com.dukascopy.api.feed.util.TimePeriodAggregationFeedDescriptor;

import forex.strategy.calculators.CCICalculator;
import forex.strategy.calculators.RSICalculator;
import forex.strategy.services.OnMessageService;
import forex.strategy.services.SubmitOrderService;

public class RsiCciStrategy implements IStrategy {

	private final String id = OnMessageService.generateStrategyId(this.getClass().getSimpleName().toUpperCase());

	private IEngine engine;
	private IHistory history;
	private IIndicators indicators;
	private int counter = 0;

	private IConsole console;

	@Configurable("Instrument")
	private Instrument instrument = Instrument.EURUSD;// Instrument.EURUSD;
	@Configurable("Period")
	private Period selectedPeriod = Period.ONE_HOUR;// FIFTEEN_MINS;
	@Configurable("SMA filter")
	private Filter indicatorFilter = Filter.WEEKENDS;
	@Configurable("Amount")
	private double defaultTradeAmount = 5.0;// 0.1;
	@Configurable("Stop loss")
	private int stopLossPips = 50;// 10;
	@Configurable("Take profit")
	private int takeProfitPips = 30;// 90;

	private IFeedDescriptor feedDescriptor;

	private double lastEquity = 0;

	@Override
	public void onStart(IContext context) throws JFException {
		this.engine = context.getEngine();
		this.history = context.getHistory();
		this.indicators = context.getIndicators();
		this.lastEquity = history.getEquity();
		this.console = context.getConsole();

		this.feedDescriptor = new TimePeriodAggregationFeedDescriptor(instrument, selectedPeriod, OfferSide.ASK,
				indicatorFilter);
		// this.feedDescriptor = new RangeBarFeedDescriptor(instrument,
		// PriceRange.TWO_PIPS, OfferSide.ASK,
		// selectedPeriod);
	}

	@Override
	public void onAccount(IAccount account) throws JFException {
	}

	@Override
	public void onMessage(IMessage message) throws JFException {
		OnMessageService.printOnMessage(console, message, id);
	}

	@Override
	public void onStop() throws JFException {
		double closePL = 0;
		// close all orders
		for (IOrder order : engine.getOrders()) {
			IOrder orderToClose = engine.getOrder(order.getLabel());
			orderToClose.close();
			closePL += orderToClose.getProfitLossInAccountCurrency();
		}
		System.err.println("TOTAL Closed Order: " + closePL + "$");
	}

	@Override
	public void onTick(Instrument instrument, ITick tick) throws JFException {
		// TODO Auto-generated method stub

	}

	@Override
	public void onBar(Instrument instrument, Period period, IBar askBar, IBar bidBar) throws JFException {
		if (!instrument.equals(instrument) || !period.equals(feedDescriptor.getPeriod()))
			return;

		final boolean isRSIBuy = RSICalculator.isRSIBuy(history, feedDescriptor, indicators);
		final boolean isRSISell = RSICalculator.isRSISell(history, feedDescriptor, indicators);
		final boolean isCCIBuy = CCICalculator.isCCIBuy(history, feedDescriptor, indicators);
		final boolean isCCISell = CCICalculator.isCCISell(history, feedDescriptor, indicators);

		double ema = indicators.ema(feedDescriptor, AppliedPrice.CLOSE, feedDescriptor.getOfferSide(), 34).calculate(1);
		if (isRSIBuy && isCCIBuy && bidBar.getClose() > ema) {
			submitOrder(OrderCommand.BUY);
			return;
		}

		if (isRSISell && isCCISell && bidBar.getClose() < ema) {
			submitOrder(OrderCommand.SELL);
			return;
		}
	}

	private IOrder submitOrder(OrderCommand orderCmd) throws JFException {

		double stopLossPrice, takeProfitPrice;

		// Calculating stop loss and take profit prices
		if (orderCmd == OrderCommand.BUY) {
			stopLossPrice = history.getLastTick(this.instrument).getBid() - getPipPrice(this.stopLossPips);
			takeProfitPrice = history.getLastTick(this.instrument).getBid() + getPipPrice(this.takeProfitPips);
			System.err.println("--> BUYING [" + history.getLastTick(this.instrument).getBid() + "]: stopLossPrice="
					+ stopLossPrice + " - takeProfitPrice:" + takeProfitPrice + "\n");
		} else {
			stopLossPrice = history.getLastTick(this.instrument).getAsk() + getPipPrice(this.stopLossPips);
			takeProfitPrice = history.getLastTick(this.instrument).getAsk() - getPipPrice(this.takeProfitPips);
			System.err.println("--> SELLING [" + history.getLastTick(this.instrument).getAsk() + "]: stopLossPrice="
					+ stopLossPrice + " - takeProfitPrice:" + takeProfitPrice + "\n");
		}

		// double newamount = calculateAmountFromEquity();
		double newamount = defaultTradeAmount; // Ha donat mes beneficis q el calcul from equity
		final String orderLabel = SubmitOrderService.getLabel(instrument, id, ++counter);

		return engine.submitOrder(orderLabel, this.instrument, orderCmd, newamount, 0, 20, stopLossPrice,
				takeProfitPrice);
	}

	private double getPipPrice(int pips) {
		return pips * this.instrument.getPipValue();
	}
}
