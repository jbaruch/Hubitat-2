/*
 * Import URL: https://raw.githubusercontent.com/jbaruch/Hubitat-2/master/WyzeHub/drivers/wyzehub-camera-group-driver.groovy
 *
 * DON'T BE A DICK PUBLIC LICENSE
 *
 * Version 1.1, December 2016
 *
 * Copyright (C) 2021 Jake Lehner
 * 
 * Everyone is permitted to copy and distribute verbatim or modified
 * copies of this license document.
 * 
 * DON'T BE A DICK PUBLIC LICENSE
 * TERMS AND CONDITIONS FOR COPYING, DISTRIBUTION AND MODIFICATION
 *
 * 1. Do whatever you like with the original work, just don't be a dick.
 * 
 *    Being a dick includes - but is not limited to - the following instances:
 *
 *    1a. Outright copyright infringement - Don't just copy this and change the name.
 *    1b. Selling the unmodified original with no work done what-so-ever, that's REALLY being a dick.
 *    1c. Modifying the original work to contain hidden harmful content. That would make you a PROPER dick.
 *
 * 2. If you become rich through modifications, related works/services, or supporting the original work,
 *    share the love. Only a dick would make loads off this work and not buy the original work's
 *    creator(s) a pint.
 * 
 * 3. Code is provided with no warranty. Using somebody else's code and bitching when it goes wrong makes
 *    you a DONKEY dick. Fix the problem yourself. A non-dick would submit the fix back.
 *
 * ===================================================================================
 *
 * Fork: github.com/jbaruch/Hubitat-2 (from fieldsjm/Hubitat-2)
 * The group switch drives NOTIFICATIONS, not camera power.
 *
 *   v1.8-notify - on()/off() now call setAllNotifications on every child camera.
 *                 Camera power moved to the explicit setCameraPower command.
 *                 switch / allOn / allOff are derived from the children's
 *                 notifications_enabled attribute instead of their switch state.
 *                 New attributes: notificationsOn, camerasOn.
 *                 Pairs with wyzehub-camera-driver.groovy v1.8-notify.
 *
 * Why: mode-based rules named "turn camera notifications off" were calling on()/off()
 * and powering the cameras down instead, which also killed event recording. Cameras
 * now stay powered and keep recording; only the push notifications get muted.
 *
 * Note this deliberately repurposes the Switch capability: on/off is notifications, and
 * camera power lives in setCameraPower with the camerasOn attribute for observability.
 *
 */

import groovy.transform.Field

public static String version() {  return "v1.8-notify"  }

public String deviceModel() { return '' }

public String groupTypeId() { return 1 }

metadata {
	definition(
		name: "WyzeHub Camera Group",
		namespace: "jakelehner",
		author: "Jake Lehner",
		importUrl: "https://raw.githubusercontent.com/jbaruch/Hubitat-2/master/WyzeHub/drivers/wyzehub-camera-group-driver.groovy"
	) {
		capability "Outlet"
		capability "Refresh"
		capability "Switch"

		attribute "allOn", "enum", ["true", "false"]
		attribute "allOff", "enum", ["true", "false"]
		attribute "notificationsOn", "enum", ["true", "false"]
		attribute "camerasOn", "enum", ["true", "false", "partial"]

		command "updateGroupState", [[
			"name":"Description",
			"description":"Recalculate group switch, allOn, allOff, and camerasOn states from current child camera states",
			"type":"STRING"
		]]
		command "setCameraPower", [[
			"name":"Power*",
			"description":"Power every camera in the group on or off. This is NOT the group switch — the switch controls notifications.",
			"type":"ENUM",
			"constraints":["true","false"]
		]]
    }

	preferences {
		input "SWITCH_MODE", "enum", title: "Switch 'on' when...",
			description: "Determines when the group switch reports 'on'. The switch reflects push notifications, not camera power.",
			options: [["any": "Any camera has notifications enabled"], ["all": "All cameras have notifications enabled"]],
			defaultValue: "any", required: true, displayDuringSetup: true
	}
}

void installed() {
    app = getApp()
	logDebug("installed()")

	sendEvent(name: 'switch', value: 'off')
	sendEvent(name: 'allOn', value: 'false')
	sendEvent(name: 'allOff', value: 'true')
	sendEvent(name: 'notificationsOn', value: 'false')
	sendEvent(name: 'camerasOn', value: 'false')

	refresh()
	initialize()
}

void updated() {
    app = getApp()
	logDebug("updated()")
	updateGroupState()
    initialize()
}

void initialize() {
    app = getApp()
	logDebug("initialize()")
}

void parse(String description) {
	app = getApp()
	logWarn("Running unimplemented parse for: '${description}'")
}

def refresh() {
	getChildDevices().each { childDevice ->
		childDevice.settingsRefresh()
		childDevice.refresh()
	}
	runIn(15, 'updateGroupState')
}

// The group switch is the NOTIFICATIONS switch. Camera power is setCameraPower().
def on() {
	logInfo("Enabling notifications on all cameras in the group")
	getChildDevices().each { childDevice ->
		childDevice.setAllNotifications("true")
	}
	runIn(20, 'updateGroupState')
}

def off() {
	logInfo("Disabling notifications on all cameras in the group")
	getChildDevices().each { childDevice ->
		childDevice.setAllNotifications("false")
	}
	runIn(20, 'updateGroupState')
}

def setCameraPower(power) {
	String value = (power?.toString() == "true") ? "true" : "false"
	logInfo("Setting camera power to ${value} on all cameras in the group")

	getChildDevices().each { childDevice ->
		if (value == "true") {
			childDevice.on()
		} else {
			childDevice.off()
		}
	}
	runIn(10, 'updateGroupState')
}

void updateGroupState() {
	logDebug("updateGroupState()")

	def children = getChildDevices()
	if (!children) {
		logDebug("No child devices found")
		return
	}

	int totalCount = children.size()
	int notifyOnCount = children.count { it.currentValue("notifications_enabled") == "true" }
	int poweredOnCount = children.count { it.currentValue("switch") == "on" }

	String allOnValue = (notifyOnCount == totalCount) ? "true" : "false"
	String allOffValue = (notifyOnCount == 0) ? "true" : "false"

	String switchMode = settings.SWITCH_MODE ?: "any"
	String switchValue
	if (switchMode == "all") {
		switchValue = (notifyOnCount == totalCount) ? "on" : "off"
	} else {
		switchValue = (notifyOnCount > 0) ? "on" : "off"
	}
	String notificationsOnValue = (switchValue == "on") ? "true" : "false"

	String camerasOnValue
	if (poweredOnCount == totalCount) {
		camerasOnValue = "true"
	} else if (poweredOnCount == 0) {
		camerasOnValue = "false"
	} else {
		camerasOnValue = "partial"
	}

	logDebug("Group state: notifications ${notifyOnCount}/${totalCount} on, power ${poweredOnCount}/${totalCount} on, mode=${switchMode}, switch=${switchValue}, allOn=${allOnValue}, allOff=${allOffValue}, camerasOn=${camerasOnValue}")

	if (device.currentValue("allOn") != allOnValue) {
		sendEvent(name: "allOn", value: allOnValue, descriptionText: "${device.displayName} allOn is ${allOnValue}")
	}
	if (device.currentValue("allOff") != allOffValue) {
		sendEvent(name: "allOff", value: allOffValue, descriptionText: "${device.displayName} allOff is ${allOffValue}")
	}
	if (device.currentValue("switch") != switchValue) {
		sendEvent(name: "switch", value: switchValue, descriptionText: "${device.displayName} notifications are ${switchValue}")
	}
	if (device.currentValue("notificationsOn") != notificationsOnValue) {
		sendEvent(name: "notificationsOn", value: notificationsOnValue, descriptionText: "${device.displayName} notificationsOn is ${notificationsOnValue}")
	}
	if (device.currentValue("camerasOn") != camerasOnValue) {
		sendEvent(name: "camerasOn", value: camerasOnValue, descriptionText: "${device.displayName} camerasOn is ${camerasOnValue}")
	}
}

private getApp() {
	app = getParent()
	while(app && app.name != "WyzeHub") {
		app = app.getParent()
	}
	return app
}

private void logDebug(message) {
	app = getApp()
	app.logDebug("[${device.label}] " + message)
}

private void logInfo(message) {
	app = getApp()
	app.logInfo("[${device.label}] " + message)
}

private void logWarn(message) {
	app = getApp()
	app.logWarn("[${device.label}] " + message)
}

private void logError(message) {
	app = getApp()
	app.logError("[${device.label}] " + message)
}
