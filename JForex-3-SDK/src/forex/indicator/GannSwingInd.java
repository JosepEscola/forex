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
 * 
 * ref:
 * http://www.x-trader.net/articulos/tecnicas-de-trading/the-new-gann-swing-chartist-plan-i.html
 *
 */
public class GannSwingInd implements IIndicator {
	private IndicatorInfo indicatorInfo;
	private InputParameterInfo[] inputParameterInfos;
	private OptInputParameterInfo[] optInputParameterInfos;
	private OutputParameterInfo[] outputParameterInfos;
	private IIndicatorContext context;

	private IBar[][] inputs = new IBar[1][];
	private double[][] outputs = new double[4][];
	private int numBarras = 2;
	private int swing = 0;
	private final int UPSWING = 1;
	private final int DOWNSWING = -1;
	private final int LINEASWING = 0; // indice del array outputs donde están los datos que dibujan la línea
	private final int SOPORTE = 1; // indice del array outputs donde están los datos que dibujan los soportes
	private final int RESISTENCIA = 2; // indice del array outputs donde están los datos que dibujan las resistencias
	private final int SENTIDOTENDENCIA = 3; // 1 alcista, -1 bajista
	private double ultimaResistencia, ultimoSoporte;

	public void onStart(IIndicatorContext context) {
		this.context = context;

		indicatorInfo = new IndicatorInfo("GannSwingInd", "Tendencia Gann Swing", "My indicators", true, false, false,
				1, 1, 4);

		inputParameterInfos = new InputParameterInfo[] {
				new InputParameterInfo("Barras", InputParameterInfo.Type.BAR), };

		optInputParameterInfos = new OptInputParameterInfo[] { new OptInputParameterInfo("Numero de barras",
				OptInputParameterInfo.Type.OTHER, new IntegerRangeDescription(2, 2, 100, 1)) };

		outputParameterInfos = new OutputParameterInfo[] { new OutputParameterInfo("Swing",
				OutputParameterInfo.Type.DOUBLE, OutputParameterInfo.DrawingStyle.LINE) {
			{
				setColor(Color.green);
				setColor2(Color.red);
			}
		}, new OutputParameterInfo("Soporte", OutputParameterInfo.Type.DOUBLE, OutputParameterInfo.DrawingStyle.DOTS) {
			{
				setColor(Color.blue);
			}
		}, new OutputParameterInfo("Resistencia", OutputParameterInfo.Type.DOUBLE,
				OutputParameterInfo.DrawingStyle.DOTS) {
			{
				setColor(Color.green);
			}
		}, new OutputParameterInfo("Sentido de la tendencia", OutputParameterInfo.Type.DOUBLE,
				OutputParameterInfo.DrawingStyle.NONE), };
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

		int j = 0;
		int t = 0;
		ultimaResistencia = Double.NaN;
		ultimoSoporte = Double.NaN;
		if (endIndex + getLookforward() >= inputs[0].length) {
			endIndex = inputs[0].length - getLookforward() - 1;
		}
		if (startIndex > endIndex) {
			return new IndicatorResult(0, 0);
		}

		for (t = startIndex, j = 0; t <= endIndex; t++, j++) {
			outputs[LINEASWING][j] = Double.NaN;
			outputs[RESISTENCIA][j] = ultimaResistencia;
			outputs[SOPORTE][j] = ultimoSoporte;
			outputs[SENTIDOTENDENCIA][j] = swing;
			if (inputs[0][t].getClose() < inputs[0][t + 1].getClose()
					&& inputs[0][t + 1].getClose() < inputs[0][t + 2].getClose() // Cierre(t)<Cierre(t+1) &
																					// Cierre(t+1)<Cierre(t+2)
					&& swing != UPSWING // La tendencia previa era bajista.
					&& inputs[0][t].getClose() >= inputs[0][t].getLow() // Cierre(t)>Pico(t)
			) {
				outputs[LINEASWING][j] = outputs[SOPORTE][j] = ultimoSoporte = inputs[0][t].getLow();
				outputs[SENTIDOTENDENCIA][j] = swing = UPSWING;
			} else if (inputs[0][t].getClose() > inputs[0][t + 1].getClose()
					&& inputs[0][t + 1].getClose() > inputs[0][t + 2].getClose() // Cierre(t)>Cierre(t+1) &
																					// Cierre(t+1)>Cierre(t+2)
					&& swing != DOWNSWING // La tendencia previa era alcista.
					&& inputs[0][t].getClose() <= inputs[0][t].getHigh() // Cierre(t)<Valle(t)
			) {
				outputs[LINEASWING][j] = outputs[RESISTENCIA][j] = ultimaResistencia = inputs[0][t].getHigh();
				outputs[SENTIDOTENDENCIA][j] = swing = DOWNSWING;
			}
		}
		outputs[LINEASWING][j - 1] = inputs[0][inputs[0].length - 1].getClose();

		return new IndicatorResult(startIndex, j, endIndex);
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
		return 0;
	}

	public int getLookforward() {
		return numBarras;
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
