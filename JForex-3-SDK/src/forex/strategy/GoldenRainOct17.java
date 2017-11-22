package forex.strategy;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.CopyOnWriteArrayList;

import com.dukascopy.api.Configurable;
import com.dukascopy.api.Filter;
import com.dukascopy.api.IAccount;
import com.dukascopy.api.IBar;
import com.dukascopy.api.IConsole;
import com.dukascopy.api.IContext;
import com.dukascopy.api.IEngine;
import com.dukascopy.api.IHistory;
import com.dukascopy.api.IIndicators;
import com.dukascopy.api.IMessage;
import com.dukascopy.api.IOrder;
import com.dukascopy.api.IStrategy;
import com.dukascopy.api.ITick;
import com.dukascopy.api.IUserInterface;
import com.dukascopy.api.Instrument;
import com.dukascopy.api.JFException;
import com.dukascopy.api.OfferSide;
import com.dukascopy.api.Period;

public class GoldenRainOct17 implements IStrategy {

	private CopyOnWriteArrayList<TradeEventAction> tradeEventActions = new CopyOnWriteArrayList<TradeEventAction>();
	private static final String DATE_FORMAT_NOW = "yyyyMMdd_HHmmss";
	private IEngine engine;
	private IConsole console;
	private IHistory history;
	private IContext context;
	private IIndicators indicators;
	private IUserInterface userInterface;

	@Configurable("_Dev_dw:")
	public double _Dev_dw = 2.0;
	@Configurable("defaultTakeProfit:")
	public int defaultTakeProfit = 6;
	@Configurable("defaultTradeAmount:")
	public double defaultTradeAmount = 10.0;
	@Configurable("_TradingHour:")
	public double _TradingHour = 7.0;
	@Configurable("defaultInstrument:")
	public Instrument defaultInstrument = Instrument.EURCHF;
	@Configurable("defaultSlippage:")
	public int defaultSlippage = 5;
	@Configurable("_Ma_type:")
	public IIndicators.MaType _Ma_type = IIndicators.MaType.SMA;
	@Configurable("_BB_per:")
	public int _BB_per = 20;
	@Configurable("_ExpectedProfit:")
	public double _ExpectedProfit = 1.0;
	@Configurable("defaultStopLoss:")
	public int defaultStopLoss = 120;
	@Configurable("_PercIncr:")
	public double _PercIncr = 100.0;
	@Configurable("defaultPeriod:")
	public Period defaultPeriod = Period.ONE_HOUR;
	@Configurable("_Dev_up:")
	public double _Dev_up = 2.0;
	@Configurable("_SMA_PER:")
	public int _SMA_PER = 50;

	private int _Distance = 15;
	private Candle candle37 = null;
	private Tick LastTick = null;
	private String AccountCurrency = "";
	private double _middleBand12;
	private boolean _SELLsignal = true;
	private int _Day;
	private double _upperBand12;
	private double _tempVar352 = 7.0;
	private double _Amount;
	private double Leverage;
	private double _TP = 320.0;
	private double _PLstrateg = 0.0;
	private int _tempVar390 = 1;
	private int _HOur;
	private double _Dist1 = 0.0;
	private double _Dist2 = 200.0;
	private double _PercAmount;
	private double _SMa;
	private int _tempVar93 = 1;
	private String _Commentar = "martin";
	private double _lowerBand12;
	private int _tempVar161 = 1;
	private boolean _ClosedByAll = true;
	private Candle LastAskCandle = null;
	private double _MID0;
	private double _SL_d;
	private int _Hour;
	private Candle LastBidCandle = null;
	private String AccountId = "";
	private double Equity;
	private IOrder _MyStrategy = null;
	private boolean _UseMartingale = true;
	private double _Commission = 0.0;
	private int _tempVar406 = 7;
	private IOrder _all = null;
	private int _tempVar296 = 1;
	private int OverWeekendEndLeverage;
	private IOrder.State _tempVar130 = IOrder.State.OPENED;
	private double _NewComm = 0.0;
	private boolean _Reverse = true;
	private boolean _tempVar255 = false;
	private double _DailyGain = 100.0;
	private int _tempVar291 = 1;
	private List<IOrder> PendingPositions = null;
	private boolean _BUYsignal = true;
	private List<IOrder> OpenPositions = null;
	private boolean _Signal = true;
	private double UseofLeverage;
	private IMessage LastTradeEvent = null;
	private boolean GlobalAccount;
	private Candle candle126 = null;
	private String _MKTordr = "mark";
	private int MarginCutLevel;
	private List<IOrder> AllPositions = null;
	private int _SL = 0;
	private boolean _OnStart = true;
	private double _Dist;
	private double _OPEN_PRICE;

	public void onStart(IContext context) throws JFException {
		this.engine = context.getEngine();
		this.console = context.getConsole();
		this.history = context.getHistory();
		this.context = context;
		this.indicators = context.getIndicators();
		this.userInterface = context.getUserInterface();

		subscriptionInstrumentCheck(defaultInstrument);

		ITick lastITick = context.getHistory().getLastTick(defaultInstrument);
		LastTick = new Tick(lastITick, defaultInstrument);

		IBar bidBar = context.getHistory().getBar(defaultInstrument, defaultPeriod, OfferSide.BID, 1);
		IBar askBar = context.getHistory().getBar(defaultInstrument, defaultPeriod, OfferSide.ASK, 1);
		LastAskCandle = new Candle(askBar, defaultPeriod, defaultInstrument, OfferSide.ASK);
		LastBidCandle = new Candle(bidBar, defaultPeriod, defaultInstrument, OfferSide.BID);

		if (indicators.getIndicator("BBANDS") == null) {
			indicators.registerDownloadableIndicator("12083", "BBANDS");
		}
		subscriptionInstrumentCheck(Instrument.fromString("EUR/CHF"));

	}

	public void onAccount(IAccount account) throws JFException {
		AccountCurrency = account.getCurrency().toString();
		Leverage = account.getLeverage();
		AccountId = account.getAccountId();
		Equity = account.getEquity();
		UseofLeverage = account.getUseOfLeverage();
		OverWeekendEndLeverage = account.getOverWeekEndLeverage();
		MarginCutLevel = account.getMarginCutLevel();
		GlobalAccount = account.isGlobal();
	}

	private void updateVariables(Instrument instrument) {
		try {
			AllPositions = engine.getOrders();
			List<IOrder> listMarket = new ArrayList<IOrder>();
			for (IOrder order : AllPositions) {
				if (order.getState().equals(IOrder.State.FILLED)) {
					listMarket.add(order);
				}
			}
			List<IOrder> listPending = new ArrayList<IOrder>();
			for (IOrder order : AllPositions) {
				if (order.getState().equals(IOrder.State.OPENED)) {
					listPending.add(order);
				}
			}
			OpenPositions = listMarket;
			PendingPositions = listPending;
		} catch (JFException e) {
			e.printStackTrace();
		}
	}

	public void onMessage(IMessage message) throws JFException {
		if (message.getOrder() != null) {
			updateVariables(message.getOrder().getInstrument());
			LastTradeEvent = message;
			for (TradeEventAction event : tradeEventActions) {
				IOrder order = message.getOrder();
				if (order != null && event != null && message.getType().equals(event.getMessageType())
						&& order.getLabel().equals(event.getPositionLabel())) {
					Method method;
					try {
						method = this.getClass().getDeclaredMethod(event.getNextBlockId(), Integer.class);
						method.invoke(this, new Integer[] { event.getFlowId() });
					} catch (SecurityException e) {
						e.printStackTrace();
					} catch (NoSuchMethodException e) {
						e.printStackTrace();
					} catch (IllegalArgumentException e) {
						e.printStackTrace();
					} catch (IllegalAccessException e) {
						e.printStackTrace();
					} catch (InvocationTargetException e) {
						e.printStackTrace();
					}
					tradeEventActions.remove(event);
				}
			}
			If_block_80(2);
		}
	}

	public void onStop() throws JFException {
	}

	public void onTick(Instrument instrument, ITick tick) throws JFException {
		LastTick = new Tick(tick, instrument);
		updateVariables(instrument);

		If_block_120(0);

	}

	public void onBar(Instrument instrument, Period period, IBar askBar, IBar bidBar) throws JFException {
		LastAskCandle = new Candle(askBar, period, instrument, OfferSide.ASK);
		LastBidCandle = new Candle(bidBar, period, instrument, OfferSide.BID);
		updateVariables(instrument);
		If_block_10(1);

	}

	public void subscriptionInstrumentCheck(Instrument instrument) {
		try {
			if (!context.getSubscribedInstruments().contains(instrument)) {
				Set<Instrument> instruments = new HashSet<Instrument>();
				instruments.add(instrument);
				context.setSubscribedInstruments(instruments, true);
				Thread.sleep(100);
			}
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	public double round(double price, Instrument instrument) {
		BigDecimal big = new BigDecimal("" + price);
		big = big.setScale(instrument.getPipScale() + 1, BigDecimal.ROUND_HALF_UP);
		return big.doubleValue();
	}

	public ITick getLastTick(Instrument instrument) {
		try {
			return (context.getHistory().getTick(instrument, 0));
		} catch (JFException e) {
			e.printStackTrace();
		}
		return null;
	}

	private void If_block_10(Integer flow) {
		Instrument argument_1 = defaultInstrument;
		Instrument argument_2 = LastBidCandle.getInstrument();
		if (argument_1 == null && argument_2 != null || (argument_1 != null && !argument_1.equals(argument_2))) {
		} else if (argument_1 != null && argument_1.equals(argument_2)) {
			If_block_11(flow);
		}
	}

	private void If_block_11(Integer flow) {
		Period argument_1 = defaultPeriod;
		Period argument_2 = LastBidCandle.getPeriod();
		if (argument_1 == null && argument_2 != null || (argument_1 != null && !argument_1.equals(argument_2))) {
		} else if (argument_1 != null && argument_1.equals(argument_2)) {
			MultipleAction_block_121(flow);
		}
	}

	private void BBANDS_block_12(Integer flow) {
		Instrument argument_1 = defaultInstrument;
		Period argument_2 = defaultPeriod;
		int argument_3 = _tempVar93;
		int argument_4 = _BB_per;
		double argument_5 = _Dev_up;
		double argument_6 = _Dev_dw;
		IIndicators.MaType argument_7 = _Ma_type;
		OfferSide[] offerside = new OfferSide[1];
		IIndicators.AppliedPrice[] appliedPrice = new IIndicators.AppliedPrice[1];
		offerside[0] = OfferSide.BID;
		appliedPrice[0] = IIndicators.AppliedPrice.CLOSE;
		Object[] params = new Object[4];
		params[0] = _BB_per;
		params[1] = _Dev_up;
		params[2] = _Dev_dw;
		params[3] = _Ma_type.ordinal();
		try {
			subscriptionInstrumentCheck(argument_1);
			long time = context.getHistory().getBar(argument_1, argument_2, OfferSide.BID, argument_3).getTime();
			Object[] indicatorResult = context.getIndicators().calculateIndicator(argument_1, argument_2, offerside,
					"BBANDS", appliedPrice, params, Filter.WEEKENDS, 1, time, 0);
			if ((new Double(((double[]) indicatorResult[0])[0])) == null) {
				this._upperBand12 = Double.NaN;
			} else {
				this._upperBand12 = (((double[]) indicatorResult[0])[0]);
			}
			if ((new Double(((double[]) indicatorResult[1])[0])) == null) {
				this._middleBand12 = Double.NaN;
			} else {
				this._middleBand12 = (((double[]) indicatorResult[1])[0]);
			}
			if ((new Double(((double[]) indicatorResult[2])[0])) == null) {
				this._lowerBand12 = Double.NaN;
			} else {
				this._lowerBand12 = (((double[]) indicatorResult[2])[0]);
			}
			If_block_13(flow);
		} catch (JFException e) {
			e.printStackTrace();
			console.getErr().println(e);
			this._upperBand12 = Double.NaN;
			this._middleBand12 = Double.NaN;
			this._lowerBand12 = Double.NaN;
		}
	}

	private void If_block_13(Integer flow) {
		int argument_1 = AllPositions.size();
		int argument_2 = 1;
		if (argument_1 < argument_2) {
			If_block_142(flow);
		} else if (argument_1 > argument_2) {
		} else if (argument_1 == argument_2) {
			PositionsViewer_block_18(flow);
		}
	}

	private void If_block_14(Integer flow) {
		double argument_1 = LastBidCandle.getClose();
		double argument_2 = _upperBand12;
		if (argument_1 < argument_2) {
			If_block_110(flow);
		} else if (argument_1 > argument_2) {
			OpenatMarket_block_16(flow);
		} else if (argument_1 == argument_2) {
			OpenatMarket_block_16(flow);
		}
	}

	private void OpenatMarket_block_16(Integer flow) {
		Instrument argument_1 = defaultInstrument;
		double argument_2 = defaultTradeAmount;
		int argument_3 = defaultSlippage;
		int argument_4 = defaultStopLoss;
		int argument_5 = defaultTakeProfit;
		String argument_6 = _Commentar;
		ITick tick = getLastTick(argument_1);

		IEngine.OrderCommand command = IEngine.OrderCommand.SELL;

		double stopLoss = tick.getAsk() + argument_1.getPipValue() * argument_4;
		double takeProfit = round(tick.getAsk() - argument_1.getPipValue() * argument_5, argument_1);

		try {
			String label = getLabel();
			IOrder order = context.getEngine().submitOrder(label, argument_1, command, argument_2, 0, argument_3,
					stopLoss, takeProfit, 0, argument_6);
		} catch (JFException e) {
			e.printStackTrace();
		}
	}

	private void OpenatMarket_block_17(Integer flow) {
		Instrument argument_1 = defaultInstrument;
		double argument_2 = defaultTradeAmount;
		int argument_3 = defaultSlippage;
		int argument_4 = defaultStopLoss;
		int argument_5 = defaultTakeProfit;
		String argument_6 = _Commentar;
		ITick tick = getLastTick(argument_1);

		IEngine.OrderCommand command = IEngine.OrderCommand.BUY;

		double stopLoss = tick.getBid() - argument_1.getPipValue() * argument_4;
		double takeProfit = round(tick.getBid() + argument_1.getPipValue() * argument_5, argument_1);

		try {
			String label = getLabel();
			IOrder order = context.getEngine().submitOrder(label, argument_1, command, argument_2, 0, argument_3,
					stopLoss, takeProfit, 0, argument_6);
		} catch (JFException e) {
			e.printStackTrace();
		}
	}

	private void PositionsViewer_block_18(Integer flow) {
		List<IOrder> argument_1 = AllPositions;
		for (IOrder order : argument_1) {
			if (order.getState() == IOrder.State.OPENED || order.getState() == IOrder.State.FILLED) {
				_all = order;
				If_block_78(flow);
			}
		}
	}

	private void If_block_63(Integer flow) {
		Instrument argument_1 = LastTradeEvent.getOrder().getInstrument();
		Instrument argument_2 = defaultInstrument;
		if (argument_1 == null && argument_2 != null || (argument_1 != null && !argument_1.equals(argument_2))) {
		} else if (argument_1 != null && argument_1.equals(argument_2)) {
			MultipleAction_block_64(flow);
		}
	}

	private void MultipleAction_block_64(Integer flow) {
		If_block_65(flow);
	}

	private void If_block_65(Integer flow) {
		IOrder.State argument_1 = LastTradeEvent.getOrder().getState();
		IOrder.State argument_2 = IOrder.State.CLOSED;
		if (argument_1 == null && argument_2 != null || (argument_1 != null && !argument_1.equals(argument_2))) {
		} else if (argument_1 != null && argument_1.equals(argument_2)) {
			MultipleAction_block_76(flow);
		}
	}

	private void If_block_69(Integer flow) {
		double argument_1 = LastTradeEvent.getOrder().getProfitLossInPips();
		double argument_2 = 0.0;
		if (argument_1 < argument_2) {
			If_block_82(flow);
		} else if (argument_1 > argument_2) {
		} else if (argument_1 == argument_2) {
		}
	}

	private void MultipleAction_block_76(Integer flow) {
		If_block_69(flow);
	}

	private void If_block_78(Integer flow) {
		double argument_1 = _all.getProfitLossInPips();
		double argument_2 = _ExpectedProfit;
		if (argument_1 < argument_2) {
		} else if (argument_1 > argument_2) {
			CloseandCancelPosition_block_79(flow);
		} else if (argument_1 == argument_2) {
			CloseandCancelPosition_block_79(flow);
		}
	}

	private void CloseandCancelPosition_block_79(Integer flow) {
		try {
			if (_all != null && (_all.getState() == IOrder.State.OPENED || _all.getState() == IOrder.State.FILLED)) {
				_all.close();
			}
		} catch (JFException e) {
			e.printStackTrace();
		}
	}

	private void If_block_80(Integer flow) {
		boolean argument_1 = _UseMartingale;
		boolean argument_2 = true;
		if (argument_1 != argument_2) {
		} else if (argument_1 == argument_2) {
			If_block_63(flow);
		}
	}

	private void If_block_82(Integer flow) {
		boolean argument_1 = LastTradeEvent.getOrder().isLong();
		boolean argument_2 = true;
		if (argument_1 != argument_2) {
			Assign_block_86(flow);
		} else if (argument_1 == argument_2) {
			Assign_block_85(flow);
		}
	}

	private void Assign_block_85(Integer flow) {
		boolean argument_1 = _tempVar255;
		_BUYsignal = argument_1;
	}

	private void Assign_block_86(Integer flow) {
		boolean argument_1 = _tempVar255;
		_SELLsignal = argument_1;
	}

	private void If_block_110(Integer flow) {
		double argument_1 = LastBidCandle.getClose();
		double argument_2 = _lowerBand12;
		if (argument_1 < argument_2) {
			OpenatMarket_block_17(flow);
		} else if (argument_1 > argument_2) {
		} else if (argument_1 == argument_2) {
			OpenatMarket_block_17(flow);
		}
	}

	private void If_block_120(Integer flow) {
		boolean argument_1 = _OnStart;
		boolean argument_2 = true;
		if (argument_1 != argument_2) {
		} else if (argument_1 == argument_2) {
			If_block_122(flow);
		}
	}

	private void MultipleAction_block_121(Integer flow) {
		GetHistoricalCandle_block_126(flow);
	}

	private void If_block_122(Integer flow) {
		int argument_1 = AllPositions.size();
		int argument_2 = 1;
		if (argument_1 < argument_2) {
			Assign_block_125(flow);
		} else if (argument_1 > argument_2) {
		} else if (argument_1 == argument_2) {
			PositionsViewer_block_123(flow);
		}
	}

	private void PositionsViewer_block_123(Integer flow) {
		List<IOrder> argument_1 = AllPositions;
		for (IOrder order : argument_1) {
			if (order.getState() == IOrder.State.OPENED || order.getState() == IOrder.State.FILLED) {
				_all = order;
				CloseandCancelPosition_block_124(flow);
			}
		}
	}

	private void CloseandCancelPosition_block_124(Integer flow) {
		try {
			if (_all != null && (_all.getState() == IOrder.State.OPENED || _all.getState() == IOrder.State.FILLED)) {
				_all.close();
				Assign_block_125(flow);
			}
		} catch (JFException e) {
			e.printStackTrace();
		}
	}

	private void Assign_block_125(Integer flow) {
		boolean argument_1 = false;
		_OnStart = argument_1;
	}

	private void GetHistoricalCandle_block_126(Integer flow) {
		Instrument argument_1 = defaultInstrument;
		Period argument_2 = defaultPeriod;
		OfferSide argument_3 = OfferSide.BID;
		int argument_4 = 0;
		subscriptionInstrumentCheck(argument_1);

		try {
			IBar tempBar = history.getBar(argument_1, argument_2, argument_3, argument_4);
			candle126 = new Candle(tempBar, argument_2, argument_1, argument_3);
		} catch (JFException e) {
			e.printStackTrace();
		}
		GetTimeUnit_block_135(flow);
	}

	private void GetTimeUnit_block_128(Integer flow) {
		long argument_1 = candle126.getTime();
		Date date = new Date(argument_1);
		Calendar calendar = GregorianCalendar.getInstance(TimeZone.getTimeZone("GMT"));
		calendar.setTime(date);
		_HOur = calendar.get(Calendar.HOUR_OF_DAY);
		BBANDS_block_12(flow);
	}

	private void GetTimeUnit_block_135(Integer flow) {
		long argument_1 = candle126.getTime();
		Date date = new Date(argument_1);
		Calendar calendar = GregorianCalendar.getInstance(TimeZone.getTimeZone("GMT"));
		calendar.setTime(date);
		_Day = calendar.get(Calendar.DAY_OF_WEEK);
		GetTimeUnit_block_128(flow);
	}

	private void If_block_133(Integer flow) {
		int argument_1 = _Day;
		int argument_2 = 2;
		if (argument_1 < argument_2) {
		} else if (argument_1 > argument_2) {
			If_block_134(flow);
		} else if (argument_1 == argument_2) {
			If_block_136(flow);
		}
	}

	private void If_block_134(Integer flow) {
		int argument_1 = _Day;
		int argument_2 = 3;
		if (argument_1 < argument_2) {
		} else if (argument_1 > argument_2) {
			If_block_138(flow);
		} else if (argument_1 == argument_2) {
			If_block_137(flow);
		}
	}

	private void If_block_136(Integer flow) {
		int argument_1 = _HOur;
		int argument_2 = 0;
		if (argument_1 < argument_2) {
		} else if (argument_1 > argument_2) {
			If_block_143(flow);
		} else if (argument_1 == argument_2) {
			If_block_14(flow);
		}
	}

	private void If_block_137(Integer flow) {
		int argument_1 = _HOur;
		int argument_2 = 0;
		if (argument_1 < argument_2) {
		} else if (argument_1 > argument_2) {
			If_block_147(flow);
		} else if (argument_1 == argument_2) {
			If_block_14(flow);
		}
	}

	private void If_block_138(Integer flow) {
		int argument_1 = _Day;
		int argument_2 = 4;
		if (argument_1 < argument_2) {
		} else if (argument_1 > argument_2) {
			If_block_140(flow);
		} else if (argument_1 == argument_2) {
			If_block_141(flow);
		}
	}

	private void If_block_140(Integer flow) {
		int argument_1 = _Day;
		int argument_2 = 5;
		if (argument_1 < argument_2) {
		} else if (argument_1 > argument_2) {
			If_block_152(flow);
		} else if (argument_1 == argument_2) {
			If_block_14(flow);
		}
	}

	private void If_block_141(Integer flow) {
		int argument_1 = _HOur;
		int argument_2 = 3;
		if (argument_1 < argument_2) {
		} else if (argument_1 > argument_2) {
			If_block_150(flow);
		} else if (argument_1 == argument_2) {
			If_block_14(flow);
		}
	}

	private void If_block_142(Integer flow) {
		int argument_1 = _Day;
		int argument_2 = 1;
		if (argument_1 < argument_2) {
		} else if (argument_1 > argument_2) {
			If_block_133(flow);
		} else if (argument_1 == argument_2) {
			If_block_14(flow);
		}
	}

	private void If_block_143(Integer flow) {
		int argument_1 = _HOur;
		int argument_2 = 4;
		if (argument_1 < argument_2) {
			If_block_14(flow);
		} else if (argument_1 > argument_2) {
			If_block_145(flow);
		} else if (argument_1 == argument_2) {
			If_block_14(flow);
		}
	}

	private void If_block_145(Integer flow) {
		int argument_1 = _HOur;
		int argument_2 = 11;
		if (argument_1 < argument_2) {
		} else if (argument_1 > argument_2) {
			If_block_146(flow);
		} else if (argument_1 == argument_2) {
			If_block_14(flow);
		}
	}

	private void If_block_146(Integer flow) {
		int argument_1 = _HOur;
		int argument_2 = 15;
		if (argument_1 < argument_2) {
		} else if (argument_1 > argument_2) {
			If_block_14(flow);
		} else if (argument_1 == argument_2) {
			If_block_14(flow);
		}
	}

	private void If_block_147(Integer flow) {
		int argument_1 = _HOur;
		int argument_2 = 6;
		if (argument_1 < argument_2) {
			If_block_14(flow);
		} else if (argument_1 > argument_2) {
			If_block_149(flow);
		} else if (argument_1 == argument_2) {
			If_block_14(flow);
		}
	}

	private void If_block_149(Integer flow) {
		int argument_1 = _HOur;
		int argument_2 = 16;
		if (argument_1 < argument_2) {
		} else if (argument_1 > argument_2) {
			If_block_14(flow);
		} else if (argument_1 == argument_2) {
			If_block_14(flow);
		}
	}

	private void If_block_150(Integer flow) {
		int argument_1 = _HOur;
		int argument_2 = _tempVar406;
		if (argument_1 < argument_2) {
			If_block_14(flow);
		} else if (argument_1 > argument_2) {
			If_block_151(flow);
		} else if (argument_1 == argument_2) {
			If_block_14(flow);
		}
	}

	private void If_block_151(Integer flow) {
		int argument_1 = _HOur;
		int argument_2 = 14;
		if (argument_1 < argument_2) {
		} else if (argument_1 > argument_2) {
			If_block_14(flow);
		} else if (argument_1 == argument_2) {
			If_block_14(flow);
		}
	}

	private void If_block_152(Integer flow) {
		int argument_1 = _Day;
		int argument_2 = 6;
		if (argument_1 < argument_2) {
		} else if (argument_1 > argument_2) {
		} else if (argument_1 == argument_2) {
			If_block_153(flow);
		}
	}

	private void If_block_153(Integer flow) {
		int argument_1 = _HOur;
		int argument_2 = 0;
		if (argument_1 < argument_2) {
		} else if (argument_1 > argument_2) {
			If_block_154(flow);
		} else if (argument_1 == argument_2) {
			If_block_14(flow);
		}
	}

	private void If_block_154(Integer flow) {
		int argument_1 = _HOur;
		int argument_2 = 7;
		if (argument_1 < argument_2) {
			If_block_14(flow);
		} else if (argument_1 > argument_2) {
			If_block_155(flow);
		} else if (argument_1 == argument_2) {
			If_block_14(flow);
		}
	}

	private void If_block_155(Integer flow) {
		int argument_1 = _HOur;
		int argument_2 = 11;
		if (argument_1 < argument_2) {
		} else if (argument_1 > argument_2) {
			If_block_156(flow);
		} else if (argument_1 == argument_2) {
			If_block_14(flow);
		}
	}

	private void If_block_156(Integer flow) {
		int argument_1 = _HOur;
		int argument_2 = 13;
		if (argument_1 < argument_2) {
			If_block_14(flow);
		} else if (argument_1 > argument_2) {
			If_block_157(flow);
		} else if (argument_1 == argument_2) {
			If_block_14(flow);
		}
	}

	private void If_block_157(Integer flow) {
		int argument_1 = _HOur;
		int argument_2 = 17;
		if (argument_1 < argument_2) {
		} else if (argument_1 > argument_2) {
			If_block_14(flow);
		} else if (argument_1 == argument_2) {
			If_block_14(flow);
		}
	}

	class Candle {

		IBar bar;
		Period period;
		Instrument instrument;
		OfferSide offerSide;

		public Candle(IBar bar, Period period, Instrument instrument, OfferSide offerSide) {
			this.bar = bar;
			this.period = period;
			this.instrument = instrument;
			this.offerSide = offerSide;
		}

		public Period getPeriod() {
			return period;
		}

		public void setPeriod(Period period) {
			this.period = period;
		}

		public Instrument getInstrument() {
			return instrument;
		}

		public void setInstrument(Instrument instrument) {
			this.instrument = instrument;
		}

		public OfferSide getOfferSide() {
			return offerSide;
		}

		public void setOfferSide(OfferSide offerSide) {
			this.offerSide = offerSide;
		}

		public IBar getBar() {
			return bar;
		}

		public void setBar(IBar bar) {
			this.bar = bar;
		}

		public long getTime() {
			return bar.getTime();
		}

		public double getOpen() {
			return bar.getOpen();
		}

		public double getClose() {
			return bar.getClose();
		}

		public double getLow() {
			return bar.getLow();
		}

		public double getHigh() {
			return bar.getHigh();
		}

		public double getVolume() {
			return bar.getVolume();
		}
	}

	class Tick {

		private ITick tick;
		private Instrument instrument;

		public Tick(ITick tick, Instrument instrument) {
			this.instrument = instrument;
			this.tick = tick;
		}

		public Instrument getInstrument() {
			return instrument;
		}

		public double getAsk() {
			return tick.getAsk();
		}

		public double getBid() {
			return tick.getBid();
		}

		public double getAskVolume() {
			return tick.getAskVolume();
		}

		public double getBidVolume() {
			return tick.getBidVolume();
		}

		public long getTime() {
			return tick.getTime();
		}

		public ITick getTick() {
			return tick;
		}
	}

	protected String getLabel() {
		String label;
		label = "IVF" + getCurrentTime(LastTick.getTime()) + generateRandom(10000) + generateRandom(10000);
		return label;
	}

	private String getCurrentTime(long time) {
		SimpleDateFormat sdf = new SimpleDateFormat(DATE_FORMAT_NOW);
		return sdf.format(time);
	}

	private static String generateRandom(int n) {
		int randomNumber = (int) (Math.random() * n);
		String answer = "" + randomNumber;
		if (answer.length() > 3) {
			answer = answer.substring(0, 4);
		}
		return answer;
	}

	class TradeEventAction {
		private IMessage.Type messageType;
		private String nextBlockId = "";
		private String positionLabel = "";
		private int flowId = 0;

		public IMessage.Type getMessageType() {
			return messageType;
		}

		public void setMessageType(IMessage.Type messageType) {
			this.messageType = messageType;
		}

		public String getNextBlockId() {
			return nextBlockId;
		}

		public void setNextBlockId(String nextBlockId) {
			this.nextBlockId = nextBlockId;
		}

		public String getPositionLabel() {
			return positionLabel;
		}

		public void setPositionLabel(String positionLabel) {
			this.positionLabel = positionLabel;
		}

		public int getFlowId() {
			return flowId;
		}

		public void setFlowId(int flowId) {
			this.flowId = flowId;
		}
	}
}
