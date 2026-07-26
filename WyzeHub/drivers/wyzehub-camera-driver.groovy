/*
 * Import URL: https://raw.githubusercontent.com/jbaruch/Hubitat-2/master/WyzeHub/drivers/wyzehub-camera-driver.groovy
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
 * Release Notes:
 *   v1.5 - Polling for event detection (motion, sound, smoke alarm, co alarm)
 *   v1.4 - Floodlight support added
 *   v1.2 - Event Recording Enable/Disable
 *   v1.1 - Initial Driver Release.
 *        - Support for Power On/Off, Notification Preferences
 *
 * ===================================================================================
 *
 * Fork: github.com/jbaruch/Hubitat-2 (from fieldsjm/Hubitat-2)
 *
 *   v1.8-notify - Notify the parent camera group when notifications_enabled changes,
 *                 so the group's notification-driven switch tracks it without waiting
 *                 for the next poll. Pairs with wyzehub-camera-group-driver.groovy
 *                 v1.8-notify, where the group switch means notifications, not power.
 */

import groovy.transform.Field

public static String version() {  return "v1.8-notify"  }

public String deviceModel() { return device.getDataValue('product_model') ?: 'WYZEC1-JZ' }

// Models that use the devicemgmt API — legacy property polling returns stale P3 for these
@Field static final List deviceMgmtApiModels = ['GW_GC1', 'GW_GC2', 'LD_CFP', 'AN_RSCW']

@Field static final String wyze_action_power_on = 'power_on'
@Field static final String wyze_action_power_off = 'power_off'
@Field static final String wyze_action_motion_on = 'motion_alarm_on'
@Field static final String wyze_action_motion_off = 'motion_alarm_off'
@Field static final String wyze_action_floodlight_on = 'floodlight_on'
@Field static final String wyze_action_floodlight_off = 'floodlight_off'

@Field static final String wyze_property_power = 'P3'
@Field static final String wyze_property_device_online = 'P5'
@Field static final String wyze_property_notifications = 'P1'
@Field static final String wyze_property_motion_record = 'P1001'
@Field static final String wyze_property_motion_notify = 'P1047'
@Field static final String wyze_property_sound_notify = 'P1048'
@Field static final String wyze_property_floodlight = 'P1056'

@Field static final String wyze_property_power_value_on = '1'
@Field static final String wyze_property_power_value_off = '0'
@Field static final String wyze_property_device_online_value_true = '1'
@Field static final String wyze_property_device_online_value_false = '0'
@Field static final String wyze_property_device_motion_record_value_true = '1'
@Field static final String wyze_property_device_motion_record_value_false = '0'
@Field static final String wyze_property_device_notifications_value_true = '1'
@Field static final String wyze_property_device_notifications_value_false = '0'
@Field static final String wyze_property_device_motion_notify_value_true = '1'
@Field static final String wyze_property_device_motion_notify_value_false = '0'
@Field static final String wyze_property_device_sound_notify_value_true = '1'
@Field static final String wyze_property_device_sound_notify_value_false = '0'
@Field static final String wyze_property_device_floodlight_value_on = '1'
@Field static final String wyze_property_device_floodlight_value_off = '2'

@Field static final String wyze_event_motion_1 = '1'
@Field static final String wyze_event_motion_2 = '6'
@Field static final String wyze_event_motion_3 = '7'
@Field static final String wyze_event_motion_4 = '13'
@Field static final String wyze_event_sound = '2'
@Field static final String wyze_event_smoke = '4'
@Field static final String wyze_event_co = '5'

@Field static final String wyze_event_tag_person = '101'
@Field static final String wyze_event_tag_face = '101001'
@Field static final String wyze_event_tag_vehicle = '102'
@Field static final String wyze_event_tag_pet = '103'
@Field static final String wyze_event_tag_package = '104'
@Field static final String wyze_event_tag_crying = '800001'
@Field static final String wyze_event_tag_barking = '800002'
@Field static final String wyze_event_tag_meowing = '800003'


metadata {
	definition(
		name: "WyzeHub Camera", 
		namespace: "jakelehner", 
		author: "Jake Lehner", 
		importUrl: "https://raw.githubusercontent.com/jbaruch/Hubitat-2/master/WyzeHub/drivers/wyzehub-camera-driver.groovy"
	) {
		capability "CarbonMonoxideDetector"
        capability "MotionSensor"
        capability "Outlet"
		capability "Refresh"
        capability "SmokeDetector"
        capability "SoundSensor"
		capability "Switch"
        
        attribute "motion_recording", "enum", ["true","false"]
        attribute "notifications_enabled", "enum", ["true","false"]
        attribute "motion_notification", "enum", ["true","false"]
        attribute "sound_notification", "enum", ["true","false"]
        attribute "floodlight_powerstate", "enum", ["true","false"]
		attribute "online", "enum", ["true","false"]
        attribute "objects_detected", "string"  //any AI detected objects (person, package, vehicle, pet, etc)
        
        command "settingsRefresh", [[
            "name":"Description",
            "description":"Poll camera settings (power, notifications, recording). Runs automatically on the Device Polling Interval.",
            "type":"STRING"
        ]]
        command(
             "setMotionRecording",
             [[
                 "name":"Enable*",
                 "description":"Enable or disable event recording triggered by motion detection",
                 "type":"ENUM",
                 "constraints":["true","false"]
             ]]
        )
        command(
             "setAllNotifications",
             [[
                 "name":"Enable*",
                 "description":"Enable or disable all push notifications (motion, sound, etc.)",
                 "type":"ENUM",
                 "constraints":["true","false"]
             ]]
        )
        command(
             "setMotionNotification",
             [[
                 "name":"Enable*",
                 "description":"Enable or disable push notifications triggered by motion events",
                 "type":"ENUM",
                 "constraints":["true","false"]
             ]]
        )
        command(
             "setSoundNotification",
             [[
                 "name":"Enable*",
                 "description":"Enable or disable push notifications triggered by sound events",
                 "type":"ENUM",
                 "constraints":["true","false"]
             ]]
        )
        command(
            "setFloodlightPowerstate",
            [[
                "name":"Enable*",
                "description":"Turn the floodlight on or off (floodlight-equipped cameras only)",
                "type":"ENUM",
                "constraints":["true","false"]
            ]]
        )
	}

	preferences {
        input "SETTING_EVENT_POLL", "number", title: "Polling Interval", description: "How often to poll device state and events (seconds); 0 to disable polling", defaultValue:120, required: true, displayDuringSetup: true
        input "SETTING_EVT_VARIANCE", "number", title: "Time since Event", description: "Input desired time since last event to be considered active (minutes)", defaultValue:1, required: true, displayDuringSetup: true
        input "SETTING_INACTIVE_TIME", "number", title: "Time to Deactivate Events", description: "Input desired time since last event to automatically deactivate (seconds)", defaultValue:30, required: true, displayDuringSetup: true
	}
}

void installed() {
    logWarn("installed()")
    sendEvent(name: 'switch', value: 'off')
    sendEvent(name: 'motion', value: 'inactive')
    initialize()
}

void updated() {
    def msg = 'updated'
    if(SETTING_EVENT_POLL == 0) msg += ", disabling polling"
    logWarn(msg)
    initialize()
}

void initialize() {
    logWarn("initialize()")
    refresh()
}

void parse(String description) {
    logWarn("Running unimplemented parse for: '${description}'")
}

def settingsRefresh() {
    app = getApp()
	logInfo("settingsRefresh: ${device.deviceNetworkId} (model: ${deviceModel()})")
	app.apiGetDevicePropertyList(device.deviceNetworkId, deviceModel()) { propertyList ->
		createDeviceEventsFromPropertyList(propertyList)
	}
}

def refresh() {
    unschedule('refresh')
    app = getApp()
	logInfo("Refreshing Device Properties and Events")
	settingsRefresh()
    app.apiGetDeviceEventList(device.deviceNetworkId) { eventList ->
		createDeviceEventsFromEventList(eventList)
	}

	if(SETTING_EVENT_POLL != 0) runIn(SETTING_EVENT_POLL, refresh)
}

def on() {
	app = getApp()
	logInfo("'On' Pressed for ${device.deviceNetworkId} (model: ${deviceModel()})")

	app.apiRunAction(device.deviceNetworkId, deviceModel(), wyze_action_power_on)
}

def off() {
	app = getApp()
	logInfo("'Off' Pressed for ${device.deviceNetworkId} (model: ${deviceModel()})")

	app.apiRunAction(device.deviceNetworkId, deviceModel(), wyze_action_power_off)
}

def setMotionRecording(motion_record) {
    app = getApp()
    logInfo("setMotionRecording() Pressed")
    logDebug('Setting Motion Recording to ' + motion_record)
    
    wyze_action = motion_record == "true" ? wyze_action_motion_on : wyze_action_motion_off

	app.apiRunAction(device.deviceNetworkId, deviceModel(), wyze_action)
        
    setMotionNotification(motion_record)
}

def setAllNotifications(all_notify) {
    logInfo("setAllNotifications() Pressed")
    logDebug('Setting All Notifications to ' + all_notify)
    
    value = all_notify == "true" ? wyze_property_device_notifications_value_true : wyze_property_device_notifications_value_false
	id = wyze_property_notifications
    
    sendProperty(id, value)
    setMotionNotification(all_notify)
    setSoundNotification(all_notify)
}

def setMotionNotification(motion_notify) {
    logInfo("setMotionNotification() Pressed")
    logDebug('Setting Motion Notifications to ' + motion_notify)
    
    value = motion_notify == "true" ? wyze_property_device_motion_notify_value_true : wyze_property_device_motion_notify_value_false
	id = wyze_property_motion_notify
    
    sendProperty(id, value)
}

def setSoundNotification(sound_notify) {
	logInfo("setSoundNotification() Pressed")
    logDebug('Setting Sound Notifications to ' + sound_notify)
    
    value = sound_notify == "true" ? wyze_property_device_sound_notify_value_true : wyze_property_device_sound_notify_value_false
	id = wyze_property_sound_notify

    sendProperty(id, value)
}

def setFloodlightPowerstate(floodlight_powerstate) {
    logInfo("setFloodlightPowerstate() Pressed")
    logDebug('Setting floodlight powerstate to ' + floodlight_powerstate)
    
    value = floodlight_powerstate == "true" ? wyze_property_device_floodlight_value_on : wyze_property_device_floodlight_value_off
    id = wyze_property_floodlight
    
    sendProperty(id, value)
}

private sendProperty(pid, pvalue) {
    app = getApp()
    app.apiSetDeviceProperty(device.deviceNetworkId, deviceModel(), pid, pvalue)
    
    runIn(10, settingsRefresh)
}
    
void createDeviceEventsFromPropertyList(List propertyList) {
	app = getApp()
    logDebug("createDeviceEventsFromPropertyList: ${device.deviceNetworkId} received ${propertyList.size()} properties")
    String eventName, eventUnit
    def eventValue // could be String or number

    propertyList.each { property ->

		propertyValue = property.value ?: property.pvalue ?: null
		logDebug("  Property: pid=${property.pid}, value=${propertyValue}")
		switch(property.pid) {
            // Switch State
			case wyze_action_power_on:
			case wyze_action_power_off:
            case wyze_property_power:
			case 'power':
				// Legacy API returns stale P3 for devicemgmt models — only trust
				// power state from on/off command callbacks (power_on/power_off pid)
				if (property.pid == wyze_property_power && deviceMgmtApiModels.contains(deviceModel())) {
					logDebug("Skipping stale P3 from legacy API for devicemgmt model ${deviceModel()}")
					break
				}

				eventName = "switch"
                eventUnit = null

				if(propertyValue == wyze_property_power_value_on) {
					eventValue = "on"
				} else if(propertyValue == wyze_action_power_on) {
					eventValue = "on"
				} else {
					eventValue = "off"
				}

				if (device.currentValue(eventName) != eventValue) {
					logDebug("Updating Property 'switch' to ${eventValue}")
					app.doSendDeviceEvent(device, eventName, eventValue, eventUnit)
					notifyParentGroup()
				}
            break

            // Device Online
            case wyze_property_device_online:
                eventName = "online"
                eventUnit = null
                eventValue = propertyValue == wyze_property_device_online_value_true ? "true" : "false"
                
				if (device.currentValue(eventName) != eventValue) {
					logDebug("Updating Property 'online' to ${eventValue}")
					app.doSendDeviceEvent(device, eventName, eventValue, eventUnit)
				}
            break
            
            // Notifications Enabled
            case wyze_property_notifications:
                eventName = "notifications_enabled"
                eventUnit = null
                eventValue = propertyValue == wyze_property_device_notifications_value_true ? "true" : "false"

				if (device.currentValue(eventName) != eventValue) {
                    logDebug("Updating Property ${eventName} to ${eventValue}")
					app.doSendDeviceEvent(device, eventName, eventValue, eventUnit)
					// The camera group's switch is derived from this attribute, not from power.
					notifyParentGroup()
				}
            break
            
            // Event Recording based on motion
            case wyze_property_motion_record:
                eventName = "motion_recording"
                eventUnit = null
                eventValue = propertyValue == wyze_property_device_motion_record_value_true ? "true" : "false"

				if (device.currentValue(eventName) != eventValue) {
                    logDebug("Updating Property ${eventName} to ${eventValue}")
					app.doSendDeviceEvent(device, eventName, eventValue, eventUnit)
				}
            break
            
            // Notificaitons based on motion
            case wyze_property_motion_notify:
                eventName = "motion_notification"
                eventUnit = null
                eventValue = propertyValue == wyze_property_device_motion_notify_value_true ? "true" : "false"

				if (device.currentValue(eventName) != eventValue) {
                    logDebug("Updating Property ${eventName} to ${eventValue}")
					app.doSendDeviceEvent(device, eventName, eventValue, eventUnit)
				}
            break

            // Notificastions based on sound
            case wyze_property_sound_notify:
                eventName = "sound_notification"
                eventUnit = null
                eventValue = propertyValue == wyze_property_device_sound_notify_value_true ? "true" : "false"

				if (device.currentValue(eventName) != eventValue) {
					logDebug("Updating Property ${eventName} to ${eventValue}")
					app.doSendDeviceEvent(device, eventName, eventValue, eventUnit)
				}
            break
			
			// Floodlight Powerstate
            case wyze_property_floodlight:
				eventName = "floodlight_powerstate"
                eventUnit = null
				eventValue = propertyValue == wyze_property_device_floodlight_value_on ? "true" : "false"

				if (device.currentValue(eventName) != eventValue) {
					logDebug("Updating Property ${eventName} to ${eventValue}")
					app.doSendDeviceEvent(device, eventName, eventValue, eventUnit)
				}
            break

        }
    }
}

void createDeviceEventsFromEventList(List eventList) {
	app = getApp()
    //logDebug(eventList)
    String eventName, eventUnit, eventValue
    
    def eventTime = eventList.event_ts[0]
    def eventType = eventList.event_value[0] 
    
    def eventTag = eventList.tag_list[0]
    def eventTagCount = eventTag.size()
    def eventTagDesc = ""

    def now = (new Date()).getTime()
    def elapsedTime = now - eventTime
    
    def evtVariance = SETTING_EVT_VARIANCE ? SETTING_EVT_VARIANCE*60000 : 60000 // 1 min, default (written in ms)
    def timeToInactive = SETTING_INACTIVE_TIME ? SETTING_INACTIVE_TIME : 30 // 30 sec default (written in s)

    //if(elapsedTime < 1800000) {
    if(elapsedTime < evtVariance) {
        eventUnit = null
        
        switch(eventType) {
            case wyze_event_motion_1:
            case wyze_event_motion_2:
            case wyze_event_motion_3:
            case wyze_event_motion_4:
            eventName = "motion"
            eventValue = "active"
            app.doSendDeviceEvent(device, eventName, eventValue, eventUnit)
            runIn(timeToInactive,autoInactiveMotion)
            break
            case wyze_event_sound:
            eventName = "sound"
            eventValue = "detected"
            app.doSendDeviceEvent(device, eventName, eventValue, eventUnit)
            runIn(timeToInactive,autoInactiveSound)
            break
            case wyze_event_smoke:
            eventName = "smoke"
            eventValue = "detected"
            app.doSendDeviceEvent(device, eventName, eventValue, eventUnit)
            runIn(timeToInactive,autoInactiveSmoke)
            break
            case wyze_event_co:
            eventName = "carbonMonoxide"
            eventValue = "detected"
            app.doSendDeviceEvent(device, eventName, eventValue, eventUnit)
            runIn(timeToInactive,autoInactiveCO)
            break
            default:
                logWarn("Event Type ${eventType} not found")
            break
        }

        if (eventTagCount > 0) {
            for(x=0;x<eventTagCount;x++){
                def tag = eventTag[x]
                if(x>0) eventTagDesc += ', '
                
                switch(tag) {
                    case wyze_event_tag_person:
                    eventTagDesc += "Person"
                    break
                    case wyze_event_tag_face:
                    eventTagDesc += "Face"
                    break
                    case wyze_event_tag_vehicle:
                    eventTagDesc += "Vehicle"
                    break
                    case wyze_event_tag_pet:
                    eventTagDesc += "Pet"
                    break
                    case wyze_event_tag_package:
                    eventTagDesc += "Package"
                    break
                    case wyze_event_tag_crying:
                    eventTagDesc += "Baby Crying"
                    break
                    case wyze_event_tag_barking:
                    eventTagDesc += "Dog Barking"
                    break
                    case wyze_event_tag_meowing:
                    eventTagDesc += "Meowing"
                    break
                    default:
                        logWarn("Unknown event tag: ${eventTag}")
                    break
                }
            }
            eventName = "objects_detected"
            eventValue = eventTagDesc
            app.doSendDeviceEvent(device, eventName, eventValue, eventUnit)
        }
        
        logInfo("${eventTagDesc ?: ''} ${eventName} detected")
    }
}    

//Automatically deactive events after user selected time period
void autoInactiveMotion() {
    eventName = "motion"
    eventValue = "inactive"
    app.doSendDeviceEvent(device, eventName, eventValue, eventUnit)
    if (device.currentValue("objects_detected") != "None") autoObjNone()
}

void autoInactiveSound() {
    eventName = "sound"
    eventValue = "not detected"
    app.doSendDeviceEvent(device, eventName, eventValue, eventUnit)
    if (device.currentValue("objects_detected") != "None") autoObjNone()
}

void autoInactiveSmoke() {
    eventName = "smoke"
    eventValue = "clear"
    app.doSendDeviceEvent(device, eventName, eventValue, eventUnit)
    if (device.currentValue("objects_detected") != "None") autoObjNone()
}

void autoInactiveCO() {
    eventName = "carbonMonoxide"
    eventValue = "clear"
    app.doSendDeviceEvent(device, eventName, eventValue, eventUnit)
    if (device.currentValue("objects_detected") != "None") autoObjNone()
}

void autoObjNone() {
    eventName = "objects_detected"
    eventValue = "None"
    app.doSendDeviceEvent(device, eventName, eventValue, eventUnit)
}

private notifyParentGroup() {
	try {
		def parent = getParent()
		if (parent && parent.name != "WyzeHub") {
			logDebug("Notifying parent group ${parent.displayName} to update state in 3s")
			parent.runIn(3, 'updateGroupState')
		}
	} catch (Exception e) {
		logDebug("No parent group to notify: ${e}")
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
