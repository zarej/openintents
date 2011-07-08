package org.openintents.tools.simulator.view.sensor.sensors;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Random;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JTextField;
import javax.swing.SwingConstants;

import org.openintents.tools.simulator.Global;
import org.openintents.tools.simulator.JTextAreaWrap;
import org.openintents.tools.simulator.model.sensor.sensors.SensorModel;
import org.openintents.tools.simulator.view.help.HelpWindow;

public abstract class SensorView extends JScrollPane {
	private static final long serialVersionUID = 6732292499469735861L;
	private static final String EMPTY_LABEL = "                 -                ";
	private static Random rand = new Random();

	protected JButton mEnabled;
	private HelpWindow helpWindow;

	// Simulation update
	protected JLabel mDefaultUpdateRateText;
	protected JLabel mCurrentUpdateRateText;

	/** Whether to form an average at each update */
	protected JCheckBox mUpdateAverage;

	// Random contribution
	protected JTextField mRandomText;

	protected JLabel mRefreshEmulatorLabel;

	protected SensorModel model;
	private JPanel insidePanel;
	private JButton helpBtn;

	public SensorView(SensorModel model) {
		super();
		this.model = model;
		setPreferredSize(new Dimension(
				(int) (Global.WIDTH * Global.SENSOR_SPLIT_RIGHT),
				(int) (Global.HEIGHT * Global.SENSOR_SPLIT_UP)));
		mEnabled = new JButton(model.getName());
		if (model.isEnabled())
			mEnabled.setBackground(Global.ENABLE);
		else
			mEnabled.setBackground(Global.DISABLE);

		mCurrentUpdateRateText = new JLabel();

		mUpdateAverage = new JCheckBox("average values");
		mUpdateAverage.setSelected(true);

		helpWindow = new HelpWindow(this);

		fillSensorPanel();
	}

	private void fillSensorPanel() {
		insidePanel = new JPanel(new GridBagLayout());

		GridBagConstraints l = new GridBagConstraints();
		l.fill = GridBagConstraints.HORIZONTAL;
		l.anchor = GridBagConstraints.NORTHWEST;
		l.gridx = 0;
		l.gridy = 0;
		getViewport().add(insidePanel);

		// update rates
		JPanel generalSettingsPanel = fillGeneralSettingsPanel();
		insidePanel.add(generalSettingsPanel, l);

		// panel settings
		JPanel sensorSettings = fillSensorSpecificSettingsPanel();
		l.gridx = 1;
		insidePanel.add(sensorSettings, l);
	}

	private JPanel fillGeneralSettingsPanel() {
		JPanel generalSettingsPanel = new JPanel();
		generalSettingsPanel.setLayout(new BoxLayout(generalSettingsPanel,
				BoxLayout.Y_AXIS));

		generalSettingsPanel.setBorder(BorderFactory
				.createTitledBorder("General Settings"));
		JPanel sensorUpdate = fillSensorUpdatePanel();
		generalSettingsPanel.add(sensorUpdate);

		// random component and update simulation
		JPanel updateRandomPanel = new JPanel(new BorderLayout());
		updateRandomPanel.add(fillSensorRandomPanel(), BorderLayout.NORTH);
		updateRandomPanel.add(updateSimulationField(), BorderLayout.SOUTH);
		generalSettingsPanel.add(updateRandomPanel);

		// help button
		helpBtn = new JButton(Global.ICON_HELP);
		helpBtn.setOpaque(false);
		helpBtn.setContentAreaFilled(false);
		helpBtn.setBorderPainted(false);
		generalSettingsPanel.add(helpBtn);
		return generalSettingsPanel;
	}

	public boolean isSensorEnabled() {
		return mEnabled.isSelected();
	}

	public void setEnabled(boolean enable) {
		mEnabled.setSelected(enable);
		mRefreshEmulatorLabel.setText("-");
	}

	public boolean updateAverage() {
		return mUpdateAverage.isSelected();
	}

	public double getRandom() {
		return getSafeDouble(mRandomText);
	}

	/**
	 * get a random number in the range -random to +random
	 * 
	 * @param random
	 *            range of random number
	 * @return random number
	 */
	public static double getRandom(double random) {
		double val;
		val = rand.nextDouble();
		return (2 * val - 1) * random;
	}

	public double getSafeDouble(JTextField textfield) {
		return getSafeDouble(textfield, 0);
	}

	/**
	 * Safely retries the double value of a text field. If the value is not a
	 * valid number, 0 is returned, and the field is marked red.
	 * 
	 * @param textfield
	 *            TextField from which the value should be read.
	 * @param defaultValue
	 *            default value if input field is invalid.
	 * @return double value.
	 */
	public double getSafeDouble(JTextField textfield, double defaultValue) {
		double value = defaultValue;

		try {
			value = Double.parseDouble(textfield.getText());
			textfield.setBackground(Color.WHITE);
		} catch (NumberFormatException e) {
			// wrong user input in box - take default values.
			value = defaultValue;
			textfield.setBackground(Color.RED);
		}
		return value;
	}

	/**
	 * Safely retries the a list of double values of a text field. If the list
	 * contains errors, null is returned, and the field is marked red.
	 * 
	 * @param textfield
	 *            TextField from which the value should be read.
	 * @return list double[] with values or null.
	 */
	public static double[] getSafeDoubleList(JTextField textfield) {
		double[] valuelist = null;
		try {
			String t = textfield.getText();
			// Now we have to split this into pieces
			String[] tlist = t.split(",");
			int len = tlist.length;
			if (len > 0) {
				valuelist = new double[len];
				for (int i = 0; i < len; i++) {
					valuelist[i] = Double.parseDouble(tlist[i]);
				}
			} else {
				valuelist = null;
			}
			textfield.setBackground(Color.WHITE);
		} catch (NumberFormatException e) {
			// wrong user input in box - take default values.
			valuelist = null;
			textfield.setBackground(Color.RED);
		}
		return valuelist;
	}

	protected double getRandomFromText() {
		return getSafeDouble(mRandomText);
	}

	public JButton getEnabled() {
		return mEnabled;
	}

	public JPanel fillSensorUpdatePanel() {
		JPanel resultPanel = new JPanel(new GridBagLayout());
		GridBagConstraints layout = new GridBagConstraints();

		resultPanel.setBorder(BorderFactory.createCompoundBorder(BorderFactory
				.createTitledBorder(
						BorderFactory.createEmptyBorder(0, 0, 0, 0),
						"Update delay"), BorderFactory.createMatteBorder(2, 0,
				0, 0, Color.GRAY)));
		JLabel nameLabel = new JLabel("Default: ", JLabel.LEFT);
		layout.gridwidth = 1;
		layout.gridx = 0;
		layout.gridy = 0;
		resultPanel.add(nameLabel, layout);

		mDefaultUpdateRateText = new JLabel();
		layout.gridx = 1;
		resultPanel.add(mDefaultUpdateRateText, layout);

		nameLabel = new JLabel("Current: ", JLabel.LEFT);
		layout.gridwidth = 1;
		layout.gridx = 0;
		layout.gridy++;
		resultPanel.add(nameLabel, layout);

		layout.gridx = 1;
		resultPanel.add(mCurrentUpdateRateText, layout);

		layout.gridwidth = 3;
		layout.gridx = 0;
		layout.gridy++;

		resultPanel.add(mUpdateAverage, layout);

		layout.gridy++;
		resultPanel.add(new JSeparator(SwingConstants.HORIZONTAL), layout);

		mDefaultUpdateRateText.setText(SensorModel.SENSOR_DELAY_NORMAL);
		mCurrentUpdateRateText.setText(SensorModel.SENSOR_DELAY_NORMAL);
		return resultPanel;
	}

	public JPanel updateSimulationField() {
		JPanel resultPanel = new JPanel();
		resultPanel
				.setBorder(BorderFactory.createCompoundBorder(BorderFactory
						.createTitledBorder(BorderFactory.createEmptyBorder(),
								"Emulator update"), BorderFactory
						.createMatteBorder(2, 0, 0, 0, Color.GRAY)));
		GridBagConstraints layout = new GridBagConstraints();
		mRefreshEmulatorLabel = new JLabel(EMPTY_LABEL);
		layout.gridx = 0;
		resultPanel.add(mRefreshEmulatorLabel, layout);
		return resultPanel;
	}

	public abstract JPanel fillSensorSpecificSettingsPanel();

	public JPanel fillSensorRandomPanel() {
		JPanel resultPanel = new JPanel(new GridBagLayout());
		resultPanel.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createTitledBorder(
						BorderFactory.createEmptyBorder(), "Random"),
				BorderFactory.createMatteBorder(2, 0, 0, 0, Color.GRAY)));
		GridBagConstraints layout = new GridBagConstraints();
		mRandomText = new JTextField(5);
		mRandomText.setText("" + model.getRandom());
		layout.gridx = 0;
		resultPanel.add(mRandomText, layout);

		JLabel label = new JLabel(" " + model.getSI(), JLabel.LEFT);
		layout.gridx = 1;
		resultPanel.add(label, layout);
		return resultPanel;
	}

	public void enableSensor(PrintWriter out, boolean enable) {
		out.println("" + isSensorEnabled());
		setEnabled(enable);
	}

	public void getSensorUpdateRate(PrintWriter out) {
		if (isSensorEnabled()) {
			double updatesPerSecond = model.getCurrentUpdateRate();
			out.println("" + updatesPerSecond);
		} else {
			// This sensor is currently disabled
			out.println("throw IllegalStateException");
		}
	}

	public void unsetSensorUpdateRate(PrintWriter out) {
		if (isSensorEnabled()) {
			out.println("OK");
			mCurrentUpdateRateText.setText("" + model.getDefaultUpdateRate());
		} else {
			// This sensor is currently disabled
			out.println("throw IllegalStateException");
		}
	}

	public void addEnable(JPanel enabledSensorsPane) {
		enabledSensorsPane.add(mEnabled);
	}

	public JCheckBox getUpdateAvg() {
		return mUpdateAverage;
	}

	public void setRefreshEmulatorTime(String message) {
		mRefreshEmulatorLabel.setText(message);
	}

	public SensorModel getModel() {
		return model;
	}

	public JButton getHelpButton() {
		return helpBtn;
	}

	public HelpWindow getHelpWindow() {
		return helpWindow;
	}

	public JPanel getHelpPanel() {
		JPanel panel = new JPanel();
		panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
		JPanel sensorSpecificHelp = getSensorSpecificHelp();
		if (sensorSpecificHelp != null) {
			// sensorSpecificHelp.setAlignmentX(Component.LEFT_ALIGNMENT);
			panel.add(sensorSpecificHelp);
		}

		JTextAreaWrap helpTextArea;

		// help for "Update delay"
		JPanel updateDelayHelp = new JPanel();
		updateDelayHelp.setLayout(new BoxLayout(updateDelayHelp,
				BoxLayout.Y_AXIS));
		updateDelayHelp.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createTitledBorder(
						BorderFactory.createEmptyBorder(0, 0, 0, 0),
						"Update delay"), BorderFactory.createMatteBorder(2, 0,
						0, 0, Color.GRAY)));

		helpTextArea = new JTextAreaWrap(
				"<b>Default:</b> is the delay to which a sensor is updating "
						+ "until register or after unregister a listener for it.<br\\>"
						+ "<b>Current:</b> is the delay to which the listener for the sensor is registered.<br\\>"
						+ "<b>Possible values for delay:</b> FASTEST(0ms), GAME(20ms), UI(60ms), NORMAL(200ms)");
		updateDelayHelp.add(helpTextArea);
		panel.add(updateDelayHelp);

		// help for "Average checkbox"
		JPanel averageHelp = new JPanel();
		averageHelp.setLayout(new BoxLayout(averageHelp, BoxLayout.Y_AXIS));
		averageHelp.setBorder(BorderFactory.createCompoundBorder(BorderFactory
				.createTitledBorder(
						BorderFactory.createEmptyBorder(0, 0, 0, 0),
						"Average checkbox"), BorderFactory.createMatteBorder(2,
				0, 0, 0, Color.GRAY)));
		helpTextArea = new JTextAreaWrap(
				"<b>Average:</b> that average sensor data should be returned instead of " +
				"instantaneous values.");
		averageHelp.add(helpTextArea);
		panel.add(averageHelp);

		// help for "Random"
		JPanel randomHelp = new JPanel();
		randomHelp.setLayout(new BoxLayout(randomHelp, BoxLayout.Y_AXIS));
		randomHelp.setBorder(BorderFactory.createCompoundBorder(BorderFactory
				.createTitledBorder(
						BorderFactory.createEmptyBorder(0, 0, 0, 0),
						"Sensor update"), BorderFactory.createMatteBorder(2,
				0, 0, 0, Color.GRAY)));
		helpTextArea = new JTextAreaWrap(
				"<b>Random:</b> the interval lenght for sensor error simulation.");
		randomHelp.add(helpTextArea);
		panel.add(randomHelp);
		
		// help for "Simulation update"
		JPanel sensorUpdateHelp = new JPanel();
		sensorUpdateHelp.setLayout(new BoxLayout(sensorUpdateHelp, BoxLayout.Y_AXIS));
		sensorUpdateHelp.setBorder(BorderFactory.createCompoundBorder(BorderFactory
				.createTitledBorder(
						BorderFactory.createEmptyBorder(0, 0, 0, 0),
						"Simulation update"), BorderFactory.createMatteBorder(2,
				0, 0, 0, Color.GRAY)));
		helpTextArea = new JTextAreaWrap(
				"<b>Emulator update:</b> shows the actual internal emulator update rate.<br\\>" +
				"<b>Update sensors:</b> sets the duration between internal updates.<br\\>" +
				"<b>Refresh after:</b> determines after how many queries by the emulator the update rates " +
					"given below are calculated and averaged over.<br\\>" +
				"<b>Sensor update:</b> shows the actual internal simulator application update rate.<br\\>");
		sensorUpdateHelp.add(helpTextArea);
		panel.add(sensorUpdateHelp);

		JPanel buttonsPanel = new JPanel();
		buttonsPanel.setLayout(new BoxLayout(buttonsPanel, BoxLayout.X_AXIS));
		JButton buttonSensor = getBrowsingButton("Android SensorEvent",
				Global.HELP_ONE_SENSOR_URL);
		buttonsPanel.add(buttonSensor);

		JButton buttonSensorSimulator = getBrowsingButton("Sensor Simulator ",
				Global.HELP_SENSOR_SIMULATOR_DESCRIPTION_URL);
		buttonsPanel.add(buttonSensorSimulator);

		JButton buttonOpenIntentsForum = getBrowsingButton("OpenIntents Forum",
				Global.HELP_OPENINTENTS_FORUM_URL);
		buttonsPanel.add(buttonOpenIntentsForum);

		JButton buttonOpenIntentsContact = getBrowsingButton(
				"OpenIntents Contact", Global.HELP_OPENINTENTS_CONTACT_URL);
		buttonsPanel.add(buttonOpenIntentsContact);
		panel.add(buttonsPanel);

		return panel;
	}

	protected abstract JPanel getSensorSpecificHelp();

	private JButton getBrowsingButton(String btnText, final String link) {
		JButton button = new JButton(btnText);
		button.addActionListener(new ActionListener() {
			@Override
			public void actionPerformed(ActionEvent arg0) {
				if (Desktop.isDesktopSupported()) {
					Desktop desktop = Desktop.getDesktop();
					if (desktop.isSupported(Desktop.Action.BROWSE)) {
						URI uri;
						try {
							uri = new URI(link);
							desktop.browse(uri);
						} catch (URISyntaxException e) {
							e.printStackTrace();
						} catch (IOException e) {
							e.printStackTrace();
						}
					}
				}
			}
		});
		return button;
	}

	public void setCurrentUpdateRate(int updatesPerSecond) {
		switch (updatesPerSecond) {
		case SensorModel.DELAY_MS_FASTEST:
			mCurrentUpdateRateText.setText(SensorModel.SENSOR_DELAY_FASTEST);
			break;
		case SensorModel.DELAY_MS_GAME:
			mCurrentUpdateRateText.setText(SensorModel.SENSOR_DELAY_GAME);
			break;
		case SensorModel.DELAY_MS_NORMAL:
			mCurrentUpdateRateText.setText(SensorModel.SENSOR_DELAY_NORMAL);
			break;
		case SensorModel.DELAY_MS_UI:
			mCurrentUpdateRateText.setText(SensorModel.SENSOR_DELAY_UI);
			break;
		default:
			mCurrentUpdateRateText.setText("Wrong update rate!");
			break;
		}
	}
}