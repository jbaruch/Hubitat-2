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
 *
 *   v1.8-notify - Add group-level notification control as a SEPARATE child switch.
 *                 on()/off() still control camera POWER, unchanged -- the Switch
 *                 capability was added to these drivers on purpose so Rule Machine
 *                 could power cameras (jakelehner/Hubitat#7), and that stays.
 *
 *                 Muting cameras is a different axis from powering them off, so it
 *                 gets its own toggle: a "<group> Notifications" child switch whose
 *                 on/off fans setAllNotifications out to every camera in the group.
 *                 A real switch, so rules, dashboards and voice control all work on
 *                 it the same way they do on the power switch.
 *
 *                 Also adds a setAllNotifications command and a notificationsOn
 *                 attribute on the group itself. Purely additive: nothing that
 *                 worked before behaves differently.
 *
 */

import groovy.transform.Field

public static String version() {  return "v1.8-notify"  }

// Child switch that exposes notification mute/unmute for the whole group.
@Field static final String notifyChildSuffix = '-notifications' 

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

		command "updateGroupState", [[
			"name":"Description",
			"description":"Recalculate group switch, allOn, and allOff states from current child camera states",
			"type":"STRING"
		]]
		command "setAllNotifications", [[
			"name":"Enable*",
			"description":"Enable or disable push notifications on every camera in the group. Same as toggling the group's Notifications child switch. Does NOT power cameras on or off.",
			"type":"ENUM",
			"constraints":["true","false"]
		]]
    }

	preferences {
		input "SWITCH_MODE", "enum", title: "Switch 'on' when...",
			description: "Determines when the group switch reports 'on'",
			options: [["any": "Any camera is on"], ["all": "All cameras are on"]],
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

	ensureNotificationDevice()
	refresh()
	initialize()
}

void updated() {
    app = getApp()
	logDebug("updated()")
	ensureNotificationDevice()
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
	// Idempotent, and self-heals the child if it was deleted by hand.
	ensureNotificationDevice()
	getCameraDevices().each { childDevice ->
		childDevice.settingsRefresh()
		childDevice.refresh()
	}
	runIn(15, 'updateGroupState')
}

// on()/off() are camera POWER, unchanged. Notifications are the child switch below.
def on() {
	getCameraDevices().each { childDevice ->
		childDevice.on()
	}
	runIn(10, 'updateGroupState')
}

def off() {
	getCameraDevices().each { childDevice ->
		childDevice.off()
	}
	runIn(10, 'updateGroupState')
}

def setAllNotifications(enable) {
	String value = (enable?.toString() == 'true') ? 'true' : 'false'
	logInfo("Setting notifications to ${value} on all cameras in the group")

	getCameraDevices().each { childDevice ->
		childDevice.setAllNotifications(value)
	}
	// Each camera re-reads its own settings ~10s after the set; recheck after that.
	runIn(20, 'updateGroupState')
}

//  ---------------------------
// | Notification child switch |
//  ---------------------------

void componentOn(childDevice) {
	logDebug("componentOn() from ${childDevice?.displayName}")
	setAllNotifications('true')
}

void componentOff(childDevice) {
	logDebug("componentOff() from ${childDevice?.displayName}")
	setAllNotifications('false')
}

void componentRefresh(childDevice) {
	logDebug("componentRefresh() from ${childDevice?.displayName}")
	// Recalculate the roll-up only. Deliberately NOT a full refresh(): the child's
	// own installed() fires refresh(), which would land back here and re-poll every
	// camera a second time. Poll the cameras from the group device instead.
	updateGroupState()
}

// The group's own children are the cameras plus, optionally, the notification
// switch. Every fan-out must exclude the latter or it gets sent camera commands.
private getCameraDevices() {
	return getChildDevices().findAll { it.deviceNetworkId != notifyChildNetworkId() }
}

private String notifyChildNetworkId() {
	return "${device.deviceNetworkId}${notifyChildSuffix}"
}

private getNotificationDevice() {
	return getChildDevice(notifyChildNetworkId())
}

private ensureNotificationDevice() {
	def child = getNotificationDevice()
	if (child) {
		return child
	}

	child = addChildDevice(
		'hubitat',
		'Generic Component Switch',
		notifyChildNetworkId(),
		[
			name: 'Camera Group Notifications',
			label: "${device.label ?: device.name} Notifications",
			isComponent: true
		]
	)
	logInfo("Created notification switch '${child.displayName}'")
	return child
}

void updateGroupState() {
	logDebug("updateGroupState()")

	def children = getCameraDevices()
	if (!children) {
		logDebug("No child devices found")
		return
	}

	int onCount = children.count { it.currentValue("switch") == "on" }
	int totalCount = children.size()

	String allOnValue = (onCount == totalCount) ? "true" : "false"
	String allOffValue = (onCount == 0) ? "true" : "false"

	String switchMode = settings.SWITCH_MODE ?: "any"
	String switchValue
	if (switchMode == "all") {
		switchValue = (onCount == totalCount) ? "on" : "off"
	} else {
		switchValue = (onCount > 0) ? "on" : "off"
	}

	logDebug("Group state: ${onCount}/${totalCount} on, mode=${switchMode}, switch=${switchValue}, allOn=${allOnValue}, allOff=${allOffValue}")

	if (device.currentValue("allOn") != allOnValue) {
		sendEvent(name: "allOn", value: allOnValue, descriptionText: "${device.displayName} allOn is ${allOnValue}")
	}
	if (device.currentValue("allOff") != allOffValue) {
		sendEvent(name: "allOff", value: allOffValue, descriptionText: "${device.displayName} allOff is ${allOffValue}")
	}
	if (device.currentValue("switch") != switchValue) {
		sendEvent(name: "switch", value: switchValue, descriptionText: "${device.displayName} switch is ${switchValue}")
	}

	updateNotificationState(children, totalCount, switchMode)
}

// Notifications are tracked on the same any/all rule as the power switch, and
// mirrored onto the notification child so it reads back like any other switch.
private void updateNotificationState(children, int totalCount, String switchMode) {
	int notifyOnCount = children.count { it.currentValue("notifications_enabled") == "true" }

	String notificationsOnValue
	if (switchMode == "all") {
		notificationsOnValue = (notifyOnCount == totalCount) ? "true" : "false"
	} else {
		notificationsOnValue = (notifyOnCount > 0) ? "true" : "false"
	}

	logDebug("Notification state: ${notifyOnCount}/${totalCount} enabled, mode=${switchMode}, notificationsOn=${notificationsOnValue}")

	if (device.currentValue("notificationsOn") != notificationsOnValue) {
		sendEvent(name: "notificationsOn", value: notificationsOnValue, descriptionText: "${device.displayName} notificationsOn is ${notificationsOnValue}")
	}

	def notifyChild = getNotificationDevice()
	if (!notifyChild) {
		return
	}

	String childSwitch = (notificationsOnValue == "true") ? "on" : "off"
	if (notifyChild.currentValue("switch") != childSwitch) {
		notifyChild.parse([[
			name: "switch",
			value: childSwitch,
			descriptionText: "${notifyChild.displayName} switch is ${childSwitch}"
		]])
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
