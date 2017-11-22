package forex.indicator;

import java.awt.Color;
import java.text.SimpleDateFormat;
import java.util.TimeZone;

import com.dukascopy.api.IBar;
import com.dukascopy.api.indicators.IIndicator;
import com.dukascopy.api.indicators.IIndicatorContext;
import com.dukascopy.api.indicators.IndicatorInfo;
import com.dukascopy.api.indicators.IndicatorResult;
import com.dukascopy.api.indicators.InputParameterInfo;
import com.dukascopy.api.indicators.IntegerRangeDescription;
import com.dukascopy.api.indicators.OptInputParameterInfo;
import com.dukascopy.api.indicators.OutputParameterInfo;

/**
 * http://www.x-trader.net/foro/viewtopic.php?f=41&t=18393&p=209128#p209128
 * http://www.x-trader.net/articulos/tecnicas-de-trading/the-new-gann-swing-chartist-plan-ii.html
 */
public class GannHiLoInd implements IIndicator {
	private IndicatorInfo indicatorInfo;
	private InputParameterInfo[] inputParameterInfos;
	private OptInputParameterInfo[] optInputParameterInfos;
	private OutputParameterInfo[] outputParameterInfos;
	private IIndicatorContext context;

	private IBar[][] inputs = new IBar[1][];
	private double[][] outputs = new double[2][];
	private int numBarras = 3;
	private final int HILO_LOW = 0; // indice del array outputs donde están los datos del HiLo_Low
	private final int HILO_HIGH = 1; // indice del array outputs donde están los datos del Hilo_High

	private double ultimaResistencia, ultimoSoporte;

	public void onStart(IIndicatorContext context) {
		this.context = context;

		indicatorInfo = new IndicatorInfo("GannHiLoInd", "Gann HiLo Activator", "My indicators", true, false, false, 1,
				1, 2);

		inputParameterInfos = new InputParameterInfo[] {
				new InputParameterInfo("Barras", InputParameterInfo.Type.BAR), };

		optInputParameterInfos = new OptInputParameterInfo[] { new OptInputParameterInfo("Numero de barras",
				OptInputParameterInfo.Type.OTHER, new IntegerRangeDescription(3, 3, 100, 1)) };

		outputParameterInfos = new OutputParameterInfo[] { new OutputParameterInfo("HiLo_Low",
				OutputParameterInfo.Type.DOUBLE, OutputParameterInfo.DrawingStyle.LINE) {
			{
				setColor(Color.blue);
				setGapAtNaN(true);
			}
		}, new OutputParameterInfo("Hilo_High", OutputParameterInfo.Type.DOUBLE,
				OutputParameterInfo.DrawingStyle.LINE) {
			{
				setColor(Color.red);
				setGapAtNaN(true);
			}
		}, };
	}

	public IndicatorResult calculate(int startIndex, int endIndex) {
		// context.getConsole().getWarn().println("1.-StartIndex " + startIndex + "
		// EndIndex " + endIndex + " ins " + inputs[0].length + " outs "
		// +outputs[0].length);
		// context.getConsole().getWarn().println("2.-0 " + inputs[0][0].getClose() + "
		// ultimo " + inputs[0][inputs[0].length-1].getClose());
		// context.getConsole().getWarn().println("3.-startIndex " +
		// inputs[0][startIndex].getClose() + " ultimo " +
		// inputs[0][endIndex].getClose() );
		// for(int f = 0; f<inputs[0].length; f++)
		// context.getConsole().getWarn().println(f+" " + inputs[0][f]);

		// calculating startIndex taking into account lookback value
		/*
		 * if (startIndex - getLookback() < 0) { startIndex -= startIndex -
		 * getLookback(); }
		 */
		startIndex = startIndex + getLookback();
		if (startIndex > endIndex) {
			return new IndicatorResult(0, 0);
		}

		// calculating value of the first bar
		// lets get sum
		double sumLow = 0;
		double sumHigh = 0;
		// double[]gann_hilo = new double[outputs[0].length];
		int i = 0;
		int x = 0;
		for (i = startIndex, x = 0; i <= endIndex; i++, x++) {
			outputs[HILO_LOW][x] = outputs[HILO_HIGH][x] = 0;
			for (int j = 1; j <= numBarras; j++) {
				outputs[HILO_LOW][x] += inputs[0][i - j].getLow();
				outputs[HILO_HIGH][x] += inputs[0][i - j].getHigh();
			}
			outputs[HILO_LOW][x] /= numBarras;
			outputs[HILO_HIGH][x] /= numBarras;

			// Si Cierre(t)<HiLo_Low(t) y Gann_HiLo(t-1) = HiLo_Low(t-1) => Gann_HiLo(t) =
			// HiLo_High(t)
			// Si Cierre(t)>HiLo_High(t) y Gann_HiLo(t-1) = HiLo_High(t-1) => Gann_HiLo(t) =
			// HiLo_Low(t)
			if (inputs[0][i].getClose() < outputs[HILO_LOW][x]) {// && gann_hilo[x-1] == outputs[HILO_LOW][x-1]){
				outputs[HILO_LOW][x] = Double.NaN;
				// gann_hilo[x] = outputs[HILO_HIGH][x];
			}
			if (inputs[0][i].getClose() > outputs[HILO_HIGH][x]) {// && gann_hilo[x-1] == outputs[HILO_HIGH][x-1]){
				outputs[HILO_HIGH][x] = Double.NaN;
				// gann_hilo[x] = outputs[HILO_LOW][x];
			}
		}

		/*
		 * for (int i = startIndex - 1; i >= startIndex - numBarras; i--) { sumLow +=
		 * inputs[0][i].getLow(); sumHigh += inputs[0][i].getHigh(); }
		 * outputs[HILO_LOW][0] = sumLow / numBarras; outputs[HILO_HIGH][0] = sumHigh /
		 * numBarras; //now calculate rest int i, j; for (i = 1, j = startIndex + 1; j
		 * <= endIndex; i++, j++) { double prevSumSubtractPrevSmmaLow =
		 * outputs[HILO_LOW][i - 1] * (numBarras - 1); outputs[HILO_LOW][i] =
		 * (prevSumSubtractPrevSmmaLow + inputs[0][j].getLow()) / numBarras;
		 * 
		 * double prevSumSubtractPrevSmmaHigh = outputs[HILO_HIGH][i - 1] * (numBarras -
		 * 1); outputs[HILO_HIGH][i] = (prevSumSubtractPrevSmmaHigh +
		 * inputs[0][j].getHigh()) / numBarras;
		 * 
		 * //Si Cierre(t)<HiLo_Low(t) y Gann_HiLo(t-1) = HiLo_Low(t-1) => Gann_HiLo(t) =
		 * HiLo_High(t) //Si Cierre(t)>HiLo_High(t) y Gann_HiLo(t-1) = HiLo_High(t-1) =>
		 * Gann_HiLo(t) = HiLo_Low(t) //if ( inputs[0][j].getClose() <
		 * outputs[HILO_LOW][i] && gann_hilo[i-1] == outputs[HILO_LOW][i-1]){ //
		 * outputs[HILO_LOW][i] = Double.NaN; // gann_hilo[i] = outputs[HILO_HIGH][i];
		 * //} //else if ( inputs[0][j].getClose() > outputs[HILO_HIGH][i] &&
		 * gann_hilo[i-1] == outputs[HILO_HIGH][i-1]){ // outputs[HILO_HIGH][i] =
		 * Double.NaN; // gann_hilo[i] = outputs[HILO_LOW][i]; //} }
		 */
		return new IndicatorResult(startIndex, x);

		/////////////////
		/*
		 * int j = 0; int t = 0;
		 * 
		 * if (endIndex + getLookforward() >= inputs[0].length) { endIndex =
		 * inputs[0].length - getLookforward()-1; } if (startIndex > endIndex) { return
		 * new IndicatorResult(0, 0); }
		 * 
		 * for (t = startIndex, j = 0; t <= endIndex; t++, j++){ outputs[LINEASWING][j]
		 * = Double.NaN; outputs[RESISTENCIA][j] = ultimaResistencia;
		 * outputs[SOPORTE][j] = ultimoSoporte; outputs[SENTIDOTENDENCIA][j] = swing;
		 * if( inputs[0][t].getClose() < inputs[0][t+1].getClose() &&
		 * inputs[0][t+1].getClose() < inputs[0][t+2].getClose() //Cierre(t)<Cierre(t+1)
		 * & Cierre(t+1)<Cierre(t+2) && swing != UPSWING //La tendencia previa era
		 * bajista. && inputs[0][t].getClose() >= inputs[0][t].getLow()
		 * //Cierre(t)>Pico(t) ){ outputs[LINEASWING][j] = outputs[SOPORTE][j] =
		 * ultimoSoporte = inputs[0][t].getLow(); outputs[SENTIDOTENDENCIA][j] = swing =
		 * UPSWING; }else if( inputs[0][t].getClose() > inputs[0][t+1].getClose() &&
		 * inputs[0][t+1].getClose() > inputs[0][t+2].getClose() //Cierre(t)>Cierre(t+1)
		 * & Cierre(t+1)>Cierre(t+2) && swing != DOWNSWING //La tendencia previa era
		 * alcista. && inputs[0][t].getClose() <= inputs[0][t].getHigh()
		 * //Cierre(t)<Valle(t) ){ outputs[LINEASWING][j] = outputs[RESISTENCIA][j] =
		 * ultimaResistencia = inputs[0][t].getHigh(); outputs[SENTIDOTENDENCIA][j] =
		 * swing = DOWNSWING; } } outputs[LINEASWING][j-1] =
		 * inputs[0][inputs[0].length-1].getClose();
		 * 
		 * return new IndicatorResult(startIndex, j, endIndex);
		 */
		//////////////////////////////
	}

	public IndicatorInfo getIndicatorInfo() {
		return indicatorInfo;
	}

	public InputParameterInfo getInputParameterInfo(int index) {
		if (index <= inputParameterInfos.length) {
			return inputParameterInfos[index];
		}
		return null;
	}

	public int getLookback() {
		return numBarras;
	}

	public int getLookforward() {
		return 0;
	}

	public OptInputParameterInfo getOptInputParameterInfo(int index) {
		if (index < optInputParameterInfos.length) {
			return optInputParameterInfos[index];
		}
		return null;
	}

	public OutputParameterInfo getOutputParameterInfo(int index) {
		if (index < outputParameterInfos.length) {
			return outputParameterInfos[index];
		}
		return null;
	}

	public void setInputParameter(int index, Object array) {
		inputs[index] = (IBar[]) array;
	}

	public void setOptInputParameter(int index, Object value) {
		numBarras = (Integer) value;
	}

	public void setOutputParameter(int index, Object array) {
		outputs[index] = (double[]) array;
	}

	public String dateToStr(long time) {
		SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss") {

			{
				setTimeZone(TimeZone.getTimeZone("GMT"));
			}
		};
		return sdf.format(time);
	}
}
