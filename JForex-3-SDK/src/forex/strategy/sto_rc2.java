package forex.strategy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.TimeZone;

import com.dukascopy.api.Configurable;
import com.dukascopy.api.Filter;
import com.dukascopy.api.IAccount;
import com.dukascopy.api.IBar;
import com.dukascopy.api.IChart;
import com.dukascopy.api.IConsole;
import com.dukascopy.api.IContext;
import com.dukascopy.api.IEngine;
import com.dukascopy.api.IEngine.OrderCommand;
import com.dukascopy.api.IHistory;
import com.dukascopy.api.IIndicators;
import com.dukascopy.api.IIndicators.AppliedPrice;
import com.dukascopy.api.IIndicators.MaType;
import com.dukascopy.api.IMessage;
import com.dukascopy.api.IOrder;
import com.dukascopy.api.IStrategy;
import com.dukascopy.api.ITick;
import com.dukascopy.api.Instrument;
import com.dukascopy.api.JFException;
import com.dukascopy.api.JFUtils;
import com.dukascopy.api.OfferSide;
import com.dukascopy.api.Period;

import forex.strategy.services.OnMessageService;
import forex.strategy.services.SubmitOrderService;

public class sto_rc2 implements IStrategy {
	private final String id = OnMessageService.generateStrategyId(this.getClass().getSimpleName().toUpperCase());
	private IAccount account;
	private IConsole console;
	private IEngine engine;
	private IHistory history;
	private IIndicators indicators;
	private JFUtils utils;

	@Configurable("Instrument")
	public Instrument instrument = Instrument.EURUSD;
	@Configurable("Time Frame")
	public Period period = Period.ONE_HOUR;

	@Configurable("Indicator Filter")
	public Filter indicatorFilter = Filter.ALL_FLATS;
	// @Configurable("Applied price")
	public AppliedPrice appliedPrice = AppliedPrice.CLOSE;
	// @Configurable("Offer side")
	public OfferSide offerSide = OfferSide.BID;
	@Configurable("STOCH Fast K period")
	public int fastKPeriod = 3;
	@Configurable("STOCH Slow K period")
	public int slowKPeriod = 5;
	@Configurable("STOCH K MA Type")
	public MaType slowKMaType = MaType.SMA;
	@Configurable("STOCH Slow D period")
	public int slowDPeriod = 5;
	@Configurable("STOCH D MA Type")
	public MaType slowDMaType = MaType.SMA;
	@Configurable(value = "STOCH Short period", readOnly = true)
	public Period shortPeriod = period;
	@Configurable("STOCH Long period")
	public Period longPeriod = Period.DAILY;
	@Configurable("Swing High/Low period")
	public int swingPeriod = 10;

	@Configurable(value = "Risk (percent)", stepSize = 0.01)
	public double riskPercent = 2.0;
	// @Configurable("Amount")
	// public double amount = 0.02;
	@Configurable(value = "Slippage (pips)", stepSize = 0.1)
	public double slippage = 0;
	@Configurable(value = "Minimal Take Profit (pips)", stepSize = 0.5)
	public int takeProfitPips = 10;
	@Configurable(value = "Minimal Stop Loss (pips)", stepSize = 0.5)
	public int stopLossPips = 10;
	@Configurable(value = "Close all on Stop? (No)")
	public boolean closeAllOnStop = false;
	// @Configurable(value="Verbose/Debug? (No)")
	public boolean verbose = false;

	@Configurable("Start Time (GMT)")
	public String startAt = "00:00";
	@Configurable("Stop Time (GMT)")
	public String stopAt = "20:00";

	private IOrder order = null, prevOrder = null;
	private int counter = 0;

	private final static double FACTOR = 0.236;
	private final static double OVERBOUGHT = 80;
	private final static double OVERSOLD = 20;
	private int hourFrom = 0, minFrom = 0;
	private int hourTo = 0, minTo = 0;
	private long time;

	@Override
	public void onStart(IContext context) throws JFException {
		account = context.getAccount();
		console = context.getConsole();
		engine = context.getEngine();
		history = context.getHistory();
		indicators = context.getIndicators();
		utils = context.getUtils();

		// re-evaluate configurables
		hourFrom = Integer.valueOf(startAt.replaceAll("[:.][0-9]+$", ""));
		minFrom = Integer.valueOf(startAt.replaceAll("^[0-9]+[:.]", ""));
		hourTo = Integer.valueOf(stopAt.replaceAll("[:.][0-9]+$", ""));
		minTo = Integer.valueOf(stopAt.replaceAll("^[0-9]+[:.]", ""));
		shortPeriod = period;

		// Add indicators for visual testing
		IChart chart = context.getChart(instrument);
		if (chart != null && engine.getType() == IEngine.Type.TEST) {
			chart.addIndicator(indicators.getIndicator("STOCH"), new Object[] { fastKPeriod, slowKPeriod,
					slowKMaType.ordinal(), slowDPeriod, slowDMaType.ordinal() });
		}

		// Recall existing; last position, if any
		this.order = null;
		for (IOrder order : engine.getOrders(instrument)) {
			if (order.getLabel().substring(0, id.length()).equals(id)) {
				if (this.order != null) {
					// console.getWarn().println(this.order.getLabel() +" Order IGNORED, manage it
					// manually");
					console.getOut().println(this.order.getLabel() + " <WARN> Order IGNORED, manage it manually");
				}
				this.order = order;
				counter = Integer.valueOf(order.getLabel().replaceAll("^.{8,8}", ""));
				// console.getNotif().println(order.getLabel() +" Order FOUND, shall try
				// handling it");
				console.getOut().println(order.getLabel() + " <NOTICE> Order FOUND, shall try handling it");
			}
		}
		if (isActive(order))
			// console.getInfo().println(order.getLabel() +" ORDER_FOUND_OK");
			console.getOut().println(order.getLabel() + " <INFO> ORDER_FOUND_OK");
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
		if (!closeAllOnStop)
			return;

		// Close all orders
		for (IOrder order : engine.getOrders(instrument)) {
			if (order.getLabel().substring(0, id.length()).equals(id))
				order.close();
		}
	}

	public void onTick(Instrument instrument, ITick tick) throws JFException {
		if (instrument != this.instrument) {
			return;
		}
	}

	@Override
	public void onBar(Instrument instrument, Period period, IBar askBar, IBar bidBar) throws JFException {
		if (instrument != this.instrument || period != this.period) {
			return;
		}

		if (time == askBar.getTime()) {
			return;
		}
		time = askBar.getTime();

		if (!isActive(order)) {
			order = null;
		}

		int K = 0;
		int D = 1;
		double[][] stochLong = indicators.stoch(instrument, longPeriod, offerSide, fastKPeriod, slowKPeriod,
				slowKMaType, slowDPeriod, slowDMaType, indicatorFilter, 2, askBar.getTime(), 0);
		double[][] stochShort = indicators.stoch(instrument, shortPeriod, offerSide, fastKPeriod, slowKPeriod,
				slowKMaType, slowDPeriod, slowDMaType, indicatorFilter, 2, askBar.getTime(), 0);

		double high = indicators.max(instrument, period, offerSide, AppliedPrice.HIGH, swingPeriod, indicatorFilter, 1,
				askBar.getTime(), 0)[0];
		double low = indicators.min(instrument, period, offerSide, AppliedPrice.LOW, swingPeriod, indicatorFilter, 1,
				askBar.getTime(), 0)[0];

		boolean isBuySignal = false;
		boolean isSellSignal = false;

		try {

			if (stochLong[K][0] > stochLong[D][0] && stochShort[K][0] > stochShort[D][0] && stochLong[K][0] < OVERBOUGHT
					&& stochLong[D][0] < OVERBOUGHT && stochShort[K][0] < OVERBOUGHT && stochShort[D][0] < OVERBOUGHT) {
				isBuySignal = true;

			} else if (stochLong[K][0] < stochLong[D][0] && stochShort[K][0] < stochShort[D][0]
					&& stochLong[K][0] > OVERSOLD && stochLong[D][0] > OVERSOLD && stochShort[K][0] > OVERSOLD
					&& stochShort[D][0] > OVERSOLD) {
				isSellSignal = true;
			}

		} catch (final ArrayIndexOutOfBoundsException e) {
			return;
		}

		// BUY
		if (isBuySignal) {
			if (order == null || !order.isLong()) {
				closeOrder(order);

				if (isRightTime(askBar.getTime(), hourFrom, minFrom, hourTo, minTo)) {
					double stopLoss = low;
					double myStopLoss = bidBar.getOpen() - getPipPrice(stopLossPips);
					double takeProfit = getRoundedPrice(bidBar.getOpen() + (high - low) * FACTOR);
					double myTakeProfit = bidBar.getOpen() + getPipPrice(takeProfitPips);

					stopLoss = Math.min(stopLoss, myStopLoss);
					takeProfit = Math.max(takeProfit, myTakeProfit);

					if (getPricePips(takeProfit - bidBar.getOpen()) >= FACTOR * 10) {
						order = submitOrder(instrument, OrderCommand.BUY, stopLoss, takeProfit);
					}
				}
			}
			// SELL
		} else if (isSellSignal) {
			if (order == null || order.isLong()) {
				closeOrder(order);

				if (isRightTime(askBar.getTime(), hourFrom, minFrom, hourTo, minTo)) {
					double stopLoss = high;
					double myStopLoss = askBar.getOpen() + getPipPrice(stopLossPips);
					double takeProfit = getRoundedPrice(askBar.getOpen() - (high - low) * FACTOR);
					double myTakeProfit = askBar.getOpen() - getPipPrice(takeProfitPips);

					stopLoss = Math.max(stopLoss, myStopLoss);
					takeProfit = Math.min(takeProfit, myTakeProfit);

					if (getPricePips(askBar.getOpen() - takeProfit) >= FACTOR * 10) {
						order = submitOrder(instrument, OrderCommand.SELL, stopLoss, takeProfit);
					}
				}
			}
		}

		if (prevOrder != null) {
			// prevOrder.waitForUpdate(200, IOrder.State.CLOSED);
			prevOrder.waitForUpdate(200);
			switch (prevOrder.getState()) {
			case CREATED:
			case CLOSED:
				this.prevOrder = null;
				break;
			default:
				// console.getWarn().println(prevOrder.getLabel() +" Closed failed!");
				console.getOut().println(prevOrder.getLabel() + " <WARN> Closed failed!");
			}
		}
	}

	private IOrder submitOrder(Instrument instrument, OrderCommand orderCommand, double stopLossPrice,
			double takeProfitPrice) throws JFException {
		double amount = getAmount(account, instrument, riskPercent, getPipPrice(stopLossPips));

		// System.err.println("CHECK INSTRUMENT: " + instrument.toString());
		// System.err.println("GetLabel() INSTRUMENT: " + getLabel(instrument));
		final String orderLabel = SubmitOrderService.getLabel(instrument, id, ++counter);

		return engine.submitOrder(orderLabel, instrument, orderCommand, amount, 0, slippage, stopLossPrice,
				takeProfitPrice);
	}

	protected void closeOrder(IOrder order) throws JFException {
		if (order != null && isActive(order)) {
			order.close();
			prevOrder = order;
			order = null;
		}
	}

	protected boolean isActive(IOrder order) throws JFException {
		if (order == null)
			return false;

		IOrder.State state = order.getState();
		return state != IOrder.State.CLOSED && state != IOrder.State.CREATED && state != IOrder.State.CANCELED ? true
				: false;
	}

	protected boolean isRightTime(long time, int fromHour, int fromMin, int toHour, int toMin) {
		Calendar start = new GregorianCalendar();
		start.setTimeZone(TimeZone.getTimeZone("GMT"));
		start.setTimeInMillis(time);
		start.set(Calendar.HOUR_OF_DAY, fromHour);
		start.set(Calendar.MINUTE, fromMin);

		Calendar stop = new GregorianCalendar();
		stop.setTimeZone(TimeZone.getTimeZone("GMT"));
		stop.setTimeInMillis(time);
		stop.set(Calendar.HOUR_OF_DAY, toHour);
		stop.set(Calendar.MINUTE, toMin);

		return start.getTimeInMillis() <= time && time <= stop.getTimeInMillis() ? true : false;
	}

	protected double getAmount(IAccount account, Instrument instrument, double riskPercent, double stopLossPrice)
			throws JFException {
		double riskAmount = account.getEquity() * riskPercent / 100;

		double pipQuote = utils.convertPipToCurrency(instrument, account.getCurrency());
		double pipBase = instrument.getPipValue();

		double factor = pipBase / pipQuote;
		double riskPips = riskAmount * factor;

		// volumeBase * stopLossPrice = riskPips
		double volumeBase = riskPips / stopLossPrice;
		double volumeBaseInMil = volumeBase / 1000000;

		return getRoundedPrice(volumeBaseInMil);
	}

	protected double getPipPrice(double pips) {
		return pips * this.instrument.getPipValue();
	}

	protected double getPricePips(double price) {
		return price / this.instrument.getPipValue();
	}

	protected double getRoundedPips(double pips) {
		BigDecimal bd = new BigDecimal(pips);
		bd = bd.setScale(1, RoundingMode.HALF_UP);
		return bd.doubleValue();
	}

	protected double getRoundedPrice(double price) {
		BigDecimal bd = new BigDecimal(price);
		bd = bd.setScale(this.instrument.getPipScale() + 1, RoundingMode.HALF_UP);
		return bd.doubleValue();
	}
}
