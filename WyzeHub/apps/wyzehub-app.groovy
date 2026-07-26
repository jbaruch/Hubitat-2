/*
 * Import URL: https://raw.githubusercontent.com/jbaruch/Hubitat-2/master/WyzeHub/apps/wyzehub-app.groovy
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
 * Release Notes:
 *   v1.9 - Add optional camera-group mute switch (App Options, default off).
 *   v1.8 - Add configurable periodic token refresh to prevent expired
 *          Device Management API tokens.
 *   v1.7 - Device Management API for newer cameras (Cam OG, etc).
 *        - Fix async callback error handling.
 *        - Camera group switch state tracking.
 *        - Improve auth settings page.
 *   v1.6 - Add Color Bulb group 12
 *   v1.5 - Bug Fix - validateAPI to call refreshToken.
 *   v1.4 - Added API Key requirement.
 *   (Modified by @fieldsjm)
 *   ---------------------------------
 *   v1.3 - Address issue with refresh token logic.
 *        - Add Light Strip Support (non-pro)
 *   v1.2 - Bugfix allowing Meshlight to be used with rules engine (thanks @bruderbell!)
 *        - Add Camera Event Recording Enable/Disable (thanks @fieldsjm!)
 *   v1.1 - Add Camera Driver (thanks @fieldsjm!)
 *        - Add Outdoor Plug
 *        - Hubitat Package Manager
 *   v1.0 - Initial Release. 
 *        - Support for Color Bulbs, Plugs, and associated groups.
 *  
 */

import groovy.json.JsonBuilder
import groovy.transform.Field
import java.security.MessageDigest
import static java.util.UUID.randomUUID

public static final String version() { return "v1.9" }

public static final String apiAppName() { return "com.hualai" }
public static final String apiAppVersion() { return "2.19.14" }

@Field static final String childNamespace = "jakelehner" 

@Field static final Map groupDriverMap = [
	1: [label: 'Camera Group', driver: 'WyzeHub Camera Group'],
	2: [label: 'Bulb Group', driver: 'WyzeHub Bulb Group'],
	5: [label: 'Plug Group', driver: 'WyzeHub Plug Group'],
	8: [label: 'Color Bulb Group', driver: 'WyzeHub Color Bulb Group'],
	12: [label: 'Color Bulb Group', driver: 'WyzeHub Color Bulb Group']
]
@Field static final List ignoreDeviceModels = [
	'WLPPO'
]
@Field static final List deviceMgmtApiModels = ['GW_GC1', 'GW_GC2', 'LD_CFP', 'AN_RSCW']

@Field static final Map driverMap = [
	'Light': [label: 'Bulb', driver: 'WyzeHub Bulb'],
	'MeshLight': [label: 'Color Bulb', driver: 'WyzeHub Color Bulb'],
	'LightStrip' : [label: 'Light Strip', driver: 'WyzeHub Color Bulb'],
	'Plug': [label: 'Plug', driver: 'WyzeHub Plug'],
	'OutdoorPlug': [label: 'Outdoor Plug', driver: 'WyzeHub Plug'],
	'Camera': [label: 'Camera', driver: 'WyzeHub Camera'],
	'default': [label: 'Unsupported Type', driver: null]
]

@Field static final String log_level_debug   = '5'
@Field static final String log_level_info    = '4'
@Field static final String log_level_notice  = '3'
@Field static final String log_level_warn    = '2'
@Field static final String log_level_error   = '1'
@Field static final String log_level_off     = '0'
@Field static final String log_level_default = '4'

String wyzeAuthBaseUrl() { return "https://auth-prod.api.wyze.com" }
String wyzeApiBaseUrl() { return "https://api.wyzecam.com" }
String wyzeDeviceMgmtBaseUrl() { return "https://devicemgmt-service-beta.wyze.com" }

Boolean isDeviceMgmtModel(String model) {
	return deviceMgmtApiModels.contains(model)
}

Map wyzeRequestHeaders() {
    return [
        "keyid":"${settings.key_id}",
        "apikey":"${settings.api_key}",
        "Content-Type": "application/json"
		//"Accept": "application/json",
		//"User-Agent": "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/14.1 Safari/605.1.15"
    ]
}

Map wyzeRequestBody() {
    return [
        'access_token': state.access_token,
		'app_name': apiAppName(),
		'app_ver': apiAppName() + "_v" + apiAppVersion(),
		'app_version': apiAppVersion(),
		'phone_id': getPhoneId(),
		'phone_system_type': 2,
		'sc': '9f275790cab94a72bd206c8876429f3c',
		'ts': (new Date()).getTime()
    ]
}

definition(
	name: 'WyzeHub',
	namespace: 'jakelehner',
	author: 'Jake Lehner',
	description: 'Hubitat Integration for Wyze WiFi devices.',
	iconUrl: '',
	iconX2Url: '',
	iconX3Url: '',
	installOnOpen: true,
	singleInstance: true,
	oauth: false,
	menu: "Integrations",
	importUrl: 'https://raw.githubusercontent.com/fieldsjm/Hubitat-2/master/WyzeHub/apps/wyzehub-app.groovy'
)

preferences 
{
   page(name: 'pageMenu')
   page(name: 'pageAuthSettings')
   page(name: 'pageDoAuth')
   page(name: 'pageDoMfaAuth')
   page(name: 'pageSelectDevices')
   page(name: 'pageSelectDeviceGroups')
}

//  ---------------------
// | App Control Methods |
//  ---------------------

def installed() 
{
	logDebug('installed()')

	clearState()	
	initialize()
	state.serverInstalled = true

	logInfo('App Installed')
}

def updated() 
{
	logDebug('updated()')
	
	if (debugOutput) runIn(300,debugOff) //disable debug logs after 5 min
	initialize()
}

def initialize() 
{
	logDebug('initialize()')

	if (!state.deviceCache) {
		state.deviceCache = [
			'groups': [:],
			'devices': [:],
			'groupDeviceMacs': []
		]
	}

	if (!state.deviceParentMap) {
		state.deviceParentMap = [:]
	}

	if(settings['addDevices']) {
		logDebug('clearing addDevices cache')
		app.removeSetting('addDevices')
	}

	if (logEnable) {
		logInfo('Logging is Enabled')
		if (debugEnabled) {
			logDebug('Debug Logging is Enabled')
		}
	}

	scheduleTokenRefresh()

	// Camera groups read notificationSwitchEnabled() when they refresh, so nudge
	// them here to pick up a change to the option straight away rather than on
	// their next poll.
	syncCameraGroupNotificationSwitches()
}

// Read by the WyzeHub Camera Group driver to decide whether to own a mute switch.
Boolean notificationSwitchEnabled() {
	return settings.createNotificationSwitch == true
}

private void syncCameraGroupNotificationSwitches() {
	getChildDevices().each { child ->
		// Duck-typed on purpose: matches only groups that actually implement the
		// mute switch, so bulb/plug groups and older driver versions are skipped.
		if (child.hasCommand('setAllNotifications')) {
			logDebug("Refreshing ${child.displayName} to apply the mute switch option")
			child.refresh()
		}
	}
}

def uninstalled() 
{
	logDebug('uninstalled()')
	
	logInfo("Uninstalling...")

	logInfo('Clearing State...')
	clearState()

	logInfo("Deleting child devices...")
	getChildDevices().each { device->
		logDebug("Deleting device: " + device.deviceNetworkId)
		deleteChildDevice(device.deviceNetworkId)
	}

	logInfo("Uninstalled")
}

//  -------
// | Pages |
//  -------

def getCopyright() {'&copy; 2021 Your Mom'}

def pageMenu() 
{
	logDebug('pageMenu()')
	
	if (settings["devicesToAdd"]) {
		logDebug("New devices selected. Creating...")
		addDevices(settings["devicesToAdd"])
		app.removeSetting('devicesToAdd')
	}

    if (settings["deviceGroupsToAdd"]) {
		logDebug("New groups selected. Creating...")
        addDeviceGroups(settings["deviceGroupsToAdd"])
		app.removeSetting('deviceGroupsToAdd')
	}
	
	if (!(settings.username && settings.password && settings.key_id && settings.api_key)) {
		logDebug('Auth Credentials not set. Forwarding to pageAuthSettings...')
		return pageAuthSettings()
	}

	initialize()

	return dynamicPage(
		name: 'pageMenu', 
		title: "${app.label}", 
		install: true, 
		uninstall: true, 
		refreshInterval: 0
	) {

		section() {
			href(name: 'hrefSelectDeviceGroups', title: 'Select Device Groups',
               description: '', page: 'pageSelectDeviceGroups', image: '')
			href(name: 'hrefSelectDevices', title: 'Select Devices',
               description: '', page: 'pageSelectDevices', image: '')
        	href(name: 'hrefAuthSettings', title: 'Configure Login Info',
				description: '', page: 'pageAuthSettings')
      	}

		section('App Options') {
			logLevelOptions = [
				'5': 'Debug',
				'4': 'Info',
				'2': 'Warn',
				'1': 'Error',
				'0': 'Off',
			]
			input name: "logLevel", type: "enum", title: "Log Level", required: true, defaultValue: '4', submitOnChange: true, options: logLevelOptions

			tokenRefreshOptions = [
				'30': 'Every 30 Minutes',
				'60': 'Every 1 Hour',
				'180': 'Every 3 Hours',
				'360': 'Every 6 Hours',
				'720': 'Every 12 Hours',
				'0': 'Disabled',
			]
			input name: "tokenRefreshInterval", type: "enum", title: "Token Refresh Interval", required: true, defaultValue: '360', submitOnChange: true, options: tokenRefreshOptions

			input name: "createNotificationSwitch", type: "bool", title: "Add a mute switch to camera groups",
				required: false, defaultValue: false
			paragraph "Adds a <b>&lt;group name&gt; Notifications</b> virtual switch beside each camera group that can be used in dashboards, automations and voice control."

		}       
      	displayFooter()
	}	
}

def pageAuthSettings() {
	logDebug('pageAuthSettings()')
	
	return dynamicPage(
		name: 'pageAuthSettings', 
		title: "${app.label} Account Info", 
		install: false, 
		uninstall: false, 
		refreshInterval: 0,
		nextPage: 'pageDoAuth'
	) {
		section('User Authentication') {
            paragraph 'Enter your Wyze account email and password.'
            input name: 'username', type: 'text', title: 'Username (Email)', required: true, submitOnChange: true
            input name: 'password', type: 'password', title: 'Password', required: true, submitOnChange: true
        }
		section('API Key') {
            paragraph 'An API Key ID and API Key are required. Generate them at the <a href="https://developer-api-console.wyze.com/" target="_blank">Wyze Developer Console</a>: sign in with your Wyze account, then click "Create an API key". Copy both the Key ID and API Key.'
            input name: 'key_id', type: 'text', title: 'Key ID', required: true, submitOnChange: true
            input name: 'api_key', type: 'text', title: 'API Key', required: true, submitOnChange: true
        }
		section('Manually Enter Tokens (Troubleshooting)') {
            input name: 'access_token', type: 'text', title: 'Access Token', required: false, submitOnChange: true
            input name: 'refresh_token', type: 'text', title: 'Refresh Token', required: false, submitOnChange: true
        }
   		displayFooter()
	}
}

def pageDoAuth() {
	logDebug('pageDoAuth()')

	if (!(settings.username && settings.password && settings.key_id && settings.api_key)) {
		logError('Made it to "Do Auth" but credentials not set? Forwarding to pageAuthSettings...')
		return pageAuthSettings()
	}

	nextPage = 'pageMenu'
	loggedIn = false
	mfaEnabled = false
	clearState()

	// Token Override?
	if (settings.access_token) {
		logDebug('Token Override')
		state.access_token = settings.access_token
		state.refresh_token = settings.refresh_token
		
	} else {
		authenticateWyzeAccount(settings.username, settings.password)

		if (state.access_token) {
			logDebug('access token found')
			logDebug(state.access_token)
			loggedIn = true
		} else if (state.mfa_options) {
			
			mfaEnabled = true
			nextPage = 'pageDoMfaAuth'

			if (state.mfa_options.contains('TotpVerificationCode')) {
				// TOTP
				logInfo('TOTP MFA Enabled')
				mfaTitle = 'TOTP MFA Enabled'
				mfaText = 'Enter TOTP Code'
				state.mfa_type = 'TotpVerificationCode'
			} else if(state.mfa_options.contains('PrimaryPhone')) {
				// SMS
				logInfo('SMS MFA Enabled')
				mfaTitle = 'SMS MFA Enabled'
				mfaText = 'Enter SMS Code'
				state.mfa_type = 'PrimaryPhone'
				sendSmsCode('Primary', state.sms_session_id, state.user_id)
			} else {
				logError('No supported MFA Types Found')
				logDebug(state.mfa_options)
			}		
		}
	}

	return dynamicPage(
		name: 'pageAuthSettings', 
		title: "${app.label} Authentication", 
		install: false, 
		uninstall: false, 
		refreshInterval: 0,
		nextPage: nextPage
	) {
		
		if (loggedIn) {
			section() {
				paragraph("Logged In!")
			}
		} else if (mfaEnabled) {
			mfaCode = null
			settings.mfaCode = null
			section('MFA Enabled') {
				input name: 'mfaCode', type: 'password', title: mfaText, defaultValue: '', required: true, submitOnChange: false
			}
		}

   		displayFooter()
	}
}

def pageDoMfaAuth() {
	logDebug('pageDoMfaAuth()')

	if (state.mfa_type == 'PrimaryPhone') {
		verificationId = state.sms_session_id
		verificationCode = settings.mfaCode
	} else if (state.mfa_type == 'TotpVerificationCode') { 
		verificationId = state.mfa_details.totp_apps[0]['app_id']
		verificationCode = settings.mfaCode
	} else {
		return 'pageAuthSettings'
	}

	loggedIn = false
	authenticateWyzeAccount(
		settings.username, 
		settings.password, 
		state.mfa_type, 
		verificationId, 
		verificationCode
	)
	
	if (state.access_token) {
		logDebug('access token found')
		loggedIn = true
	} else {
		logError('MFA Login Error')
	}

	return dynamicPage(
		name: 'pageAuthSettings', 
		title: "${app.label} Authentication", 
		install: false, 
		uninstall: false, 
		refreshInterval: 0,
		nextPage: 'pageMenu'
	) {
		
		if (loggedIn) {
			section() {
				paragraph("Logged In!")
			}
		} else {
			section() {
				"Failed logging in with MFA."
			}
		}

   		displayFooter()
	}

}

def pageSelectDeviceGroups() {
    logDebug('pageSelectDeviceGroups()')
	
	updateDeviceCache() { response ->
		deviceCache = response
	}
		
	List newGroups = []
	List unsupportedGroups = []
	
    if (deviceCache.groups) {
		deviceCache.groups.each { mac, group ->
			groupType = groupDriverMap[group.group_type_id]
			networkId = generateGroupNetworkId(group)

			if (getChildDevice(networkId)) {
				logDebug("${group.group_name} (${networkId}) already exists. Skipping...")
				return
			}

			if(!groupType) {
				logDebug("${group.group_name} (${networkId}) unsupported. Skipping...")
				unsupportedGroups << group
				return
			} 
			
			logDebug("${group.group_name} (${networkId}) found. Adding to selection list...")
			
			newGroups << [(group.group_id): "[${groupType.label}] ${group.group_name}"]
		}

		// Sort
		newGroups = newGroups.sort { a, b ->
			a.entrySet().iterator().next()?.value <=> b.entrySet().iterator().next()?.value
		}
		unsupportedGroups = unsupportedGroups.sort { it.value }

	}

	return dynamicPage(
		name: 'pageSelectDeviceGroups',
		title: "${app.label} Device Group Selection",
		install: false,
		uninstall: false,
		refreshInterval: 0,
		nextPage: 'pageMenu'
	) {
        
        section() {
            paragraph("This page will list any <strong>new</strong> supported device groups.")
        }
		
		if (!newGroups) {
			section("No New Device Groups Found...") {
				input(name: "btnDeviceRefresh", type: "button", title: "Refresh", submitOnChange: true)
			}
		} else {
			section('Add Device Groups') {
				input(name: "deviceGroupsToAdd", type: "enum", title: "Select Device Groups to add:",
					submitOnChange: false, multiple: true, options: newGroups)
			}
		}

		if(unsupportedGroups) {
			section('Unsupported Device Groups') {
				unsupportedGroups.each{ group ->
					paragraph " - [${group.group_type_id}] ${group.group_name}"
				}
			}
		}

   		displayFooter()
	}	
}

def pageSelectDevices() {
	logDebug('pageSelectDevices()')
	
	updateDeviceCache() { response ->
		deviceCache = response
	}
		
	List newDevices = []
	List unsupportedDevices = []
	
	if (deviceCache.devices) {
		deviceCache.devices.each { mac, device ->
			if (ignoreDeviceModels.contains(device.product_model)) {
				logDebug("${device.nickname} (${device.mac}) is ignored device model. Skipping...")
				return
			}

			productType = driverMap[device.product_type]
			
			if (getChildDevice(device.mac)) {
				logDebug("${device.nickname} (${device.mac}) already exists. Skipping...")
				return
			}

			if (deviceCache.groupDeviceMacs.contains(device.mac)) {
				logDebug("${device.nickname} (${device.mac}) belongs to a group. Skipping...")
				return
			}
			
			if(!productType) {
				logDebug("${device.nickname} (${device.mac}) unsupported. Model: ${device.product_model}. Type: ${device.product_type}. Skipping...")
				unsupportedDevices << device
				return
			} 
			
			logDebug("${device.nickname} (${device.mac}) found. Adding to selection list...")
			
			newDevices << [(device.mac): "[${productType.label}] ${device.nickname}"]
		}

		// Sort
		newDevices = newDevices.sort { a, b ->
			a.entrySet().iterator().next()?.value <=> b.entrySet().iterator().next()?.value
		}
		unsupportedDevices = unsupportedDevices.sort { it.value }

	}

	return dynamicPage(
		name: 'pageSelectDevices',
		title: "${app.label} Device Selection",
		install: false,
		uninstall: false,
		refreshInterval: 0,
		nextPage: 'pageMenu'
	) {

        section() {
            paragraph('This page will list any <strong>new</strong> devices that <strong>do not belong to a device group</strong>.')
        }
		
		if (!newDevices) {
			section("No New Devices Found...") {
				input(name: "btnDeviceRefresh", type: "button", title: "Refresh", submitOnChange: true)
			}
		} else {
			section('Add Devices') {
				input(name: "devicesToAdd", type: "enum", title: "Select Devices to add:",
					submitOnChange: false, multiple: true, options: newDevices)
			}
		}

		if(unsupportedDevices) {
			section('Unsupported Devices') {
				unsupportedDevices.each{ device ->
					paragraph " - [${device.product_type}] ${device.nickname}"
				}
			}
		}

   		displayFooter()
	}	
}

def displayFooter() {
	section{
		paragraph getFormat("line")
		paragraph "<div style='color:#1A77C9;text-align:center;font-weight:small;font-size:9px'>Developed by: Jake<br/>Current Version: ${version()} -  ${getCopyright()}</div>"
    }
}

def getFormat(type){
	if(type == "line") return "\n<hr style='background-color:#1A77C9; height: 1px; border: 0;'></hr>"
}

//  --------------
// | Biznas Logic |
//  --------------

def authenticateWyzeAccount(String username, String password, String mfaType = null, String verificationId = null, String verificationCode = null) {
    logInfo('Authenticating User...')

	body = [
		'email': username,
		'password': hashPassword(password)
	]

	if (mfaType) {
		body['mfa_type'] = mfaType
		body['verification_id'] = verificationId
		body['verification_code'] = verificationCode
	}

	logDebug(body)

    params = [
		'uri'                	: wyzeAuthBaseUrl(),
		'headers'            	: wyzeRequestHeaders(),
		'requestContentType' 	: "application/json; charset=utf-8",
		'path'			     	: "/api/user/login",
		'body' 					: body
	]

	try {
		httpPost(params) { response ->
			logInfo("Login Request was OK: ${response.status}")
			logDebug(response.data)
			state.access_token   = response.data?.access_token
			state.refresh_token  = response.data?.refresh_token
			state.user_id        = response.data?.user_id
			state.mfa_options 	 = response.data?.mfa_options
			state.mfa_details 	 = response.data?.mfa_details
			state.sms_session_id = response.data?.sms_session_id
			state.statusText	 = "Success"
		}
	} catch (Exception e) {
		logError("Login Failed with Exception: ${e}")
		clearState()
		state.statusText = "Login Exception: '${e}'"
		return false
	}

		return true
}

private def sendSmsCode(String mfaPhoneType, String smsSessionId, String userId) {
	logInfo('Sending SMS Code to ' + mfaPhoneType)

	params = [
		'uri'                : wyzeAuthBaseUrl(),
		'headers'            : wyzeRequestHeaders(),
		'requestContentType' : "application/json; charset=utf-8",
		'path'			     : "/user/login/sendSmsCode",
		'query' : [
			'mfaPhoneType': mfaPhoneType,
			'sessionId': smsSessionId,
			'userId': userId,
		]
	]

	try {
		httpPost(params) { response ->
			logDebug("Send SMS was OK: ${response.status}")
			state.sms_session_id = "${response.data?.session_id}"
		}
	} catch (Exception e) {
		logError("Send SMS Failed with Exception: ${e}")
		clearState()
		state.statusText = "Send SMS Exception: '${e}'"
		return false
	}

		return true
}

private def updateDeviceCache(Closure closure = null) {
	logDebug("updateDeviceCache()")
	state.deviceCache = [
		'groups': [:],
		'devices': [:],
		'groupDeviceMacs': []
	]
	requestBody = wyzeRequestBody() + ['sv': 'c417b62d72ee44bf933054bdca183e77']
	apiPost('/app/v2/home_page/get_object_list', requestBody) { response ->

		response.data.device_group_list.each { deviceGroup ->
			state.deviceCache['groups'][deviceGroup.group_id] = deviceGroup
			deviceGroup.device_list.each {
				state.deviceCache['groupDeviceMacs'] << it.device_mac
			}
		}

		response.data.device_list.each { device ->
			state.deviceCache['devices'][device.mac] = device
		}

		if(closure) {
			closure(state.deviceCache)
		}
	}
}

private def Map getDeviceCache() {
	return state.deviceCache
}

private def Map getDeviceFromCache(String mac) {
	return state.deviceCache['devices'][mac]
}

private def Map getDeviceGroupFromCache(String id) {
	return state.deviceCache['groups'][id]
}

private def Map getDeviceListFromCache() {
	return state.deviceCache['devices']
}

private def Map getDeviceGroupListFromCache() {
	return state.deviceCache['groups']
}

private def addDeviceGroups(List deviceGroupIds) {
	logDebug('addDeviceGroups()')

	deviceGroupIds.each { deviceGroupId ->
		Map deviceGroupFromCache = getDeviceGroupFromCache(deviceGroupId)
        
		if (deviceGroupFromCache) {
			logInfo("Adding device group ${deviceGroupFromCache.group_name} with id ${deviceGroupFromCache.id}")
		
			driver = groupDriverMap[deviceGroupFromCache.group_type_id].driver
			if (!driver) {
				logError("Driver not found. Unsupported Device Group Type: ${deviceGroupFromCache.group_type_id}")
				return
			}

            groupNetworkId = generateGroupNetworkId(deviceGroupFromCache)
			deviceProps = [
				name: driver, 
				label: deviceGroupFromCache.group_name,
			]
			groupDevice = addChildDevice(childNamespace, driver, groupNetworkId, deviceProps)
            
            // Add Child Devices
            deviceGroupFromCache.device_list.each { device ->
                mac = device.device_mac
                Map deviceFromCache = getDeviceFromCache(mac)
                if (deviceFromCache) {
                    logInfo("Adding device group child device type ${deviceFromCache.product_type} with mac ${mac}")
                
                    driver = driverMap[deviceFromCache.product_type].driver
                    if (!driver) {
                        logError("Driver not found. Unsupported Device Type: ${deviceFromCache.product_type}")
                        return
                    }
					
                    deviceProps = [
                        name: (driver), 
                        label: (deviceFromCache.nickname),
                        deviceModel: (deviceFromCache.product_model)
                    ]

					state.deviceParentMap[mac] = groupNetworkId
                    device = groupDevice.addChildDevice(childNamespace, driver, deviceFromCache.mac, deviceProps)

					deviceFromCache.each { key, value ->
						if (!(key && value)) {
							logInfo('key or value not set')
							return
						}
						logInfo("setting ${key} to ${value}")
						device.updateDataValue(key, value.toString())
					}
                }
            }
		}
	}
}

private def addDevices(List deviceMacs) {
	logDebug('addDevices()')

	deviceMacs.each { mac ->
		Map deviceFromCache = getDeviceFromCache(mac)
		if (deviceFromCache) {
			logInfo("Adding device type ${deviceFromCache.product_type} with mac ${mac}")
		
			driver = driverMap[deviceFromCache.product_type].driver
			if (!driver) {
				logError("Driver not found. Unsupported Device Type: ${deviceFromCache.product_type}")
				return
			}
			deviceProps = [
				name: (driver), 
				label: (deviceFromCache.nickname),
				deviceModel: (deviceFromCache.product_model)
			]
			device = addChildDevice(childNamespace, driver, deviceFromCache.mac, deviceProps)

			deviceFromCache.each { key, value ->
				if (!(key && value)) {
					return
				}
				device.updateDataValue(key, value.toString())
			}
		}
	}
}

private def doSendDeviceEvent(com.hubitat.app.DeviceWrapper device, eventName, eventValue, eventUnit) {
	logDebug("doSendDeviceEvent()")

	String descriptionText = "${device.displayName} ${eventName} is ${eventValue}${eventUnit ?: ''}"

    eventData = [
		'name': eventName,
		'value': eventValue,
        'unit': eventUnit,
		'description': descriptionText,
		'isStateChange': true
	]

	logDebug('Sending event data...')
	logDebug(eventData)

	sendEvent(device, eventData)
}

def apiGetDevicePropertyList(String deviceMac, String deviceModel, Closure closure = {}) {
	logDebug("apiGetDevicePropertyList()")

	if (isDeviceMgmtModel(deviceModel)) {
		logInfo("apiGetDevicePropertyList: using devicemgmt API for model ${deviceModel}")
		apiGetDevicePropertyListDeviceMgmt(deviceMac, deviceModel, closure)
	} else {
		requestBody = wyzeRequestBody() + [
			'sv': 'c417b62d72ee44bf933054bdca183e77',
			'device_mac': deviceMac,
			'device_model': deviceModel
		]

		callbackData = [
			'deviceNetworkId': deviceMac
		]

		asyncapiPost('/app/v2/device/get_property_list', requestBody, 'deviceEventsCallback', callbackData)
	}
}

private def apiGetDevicePropertyListDeviceMgmt(String deviceMac, String deviceModel, Closure closure = {}) {
	logDebug("apiGetDevicePropertyListDeviceMgmt()")

	// Request iot-device properties (power state) from devicemgmt API
	requestBody = [
		'capabilities': [
			[
				'iid': 1,
				'name': 'iot-device',
				'properties': ['iot-state', 'iot-power', 'push-switch']
			],
			[
				'iid': 2,
				'name': 'camera',
				'properties': ['motion-detect-recording']
			]
		],
		'nonce': (new Date()).getTime(),
		'targetInfo': [
			'id': deviceMac,
			'productModel': deviceModel,
			'type': 'DEVICE'
		]
	]

	callbackData = [
		'deviceNetworkId': deviceMac
	]

	asyncapiPostDeviceMgmt('/device-management/api/device-property/get_iot_prop', requestBody, 'deviceMgmtPropertyCallback', callbackData)
}

//Polls for the most recent event detected (known for cameras: motion, sound, smoke alarm, CO alarm), limited to 24 hour range
def apiGetDeviceEventList(String deviceMac, Closure closure = {}) {
	logDebug("apiGetDeviceEventList()")
    
    //24 Hour Search Period
    def endTime = (new Date()).getTime()
    def beginTime = endTime - 86400000
	
	requestBody = wyzeRequestBody() + [
		'sv': 'bdcb412e230049c0be0916e75022d3f3',
		'device_mac': deviceMac,
        'begin_time': beginTime,
        'end_time': endTime,
        'order_by': 2,
        'count': 1,
	]

	callbackData = [
		'deviceNetworkId': deviceMac
	]

	asyncapiPost('/app/v2/device/get_event_list', requestBody, 'deviceEventValueCallback', callbackData)

}

def apiRunAction(String deviceMac, String deviceModel, String actionKey, Closure closure = {}) {
	logDebug("apiRunAction()")
	logInfo("apiRunAction: mac=${deviceMac}, model=${deviceModel}, actionKey=${actionKey}")

	if (isDeviceMgmtModel(deviceModel)) {
		logInfo("Routing to Device Management API for model ${deviceModel}")
		apiRunActionDeviceMgmt(deviceMac, deviceModel, actionKey, closure)
	} else {
		apiRunActionLegacy(deviceMac, deviceModel, actionKey, closure)
	}
}

private def apiRunActionLegacy(String deviceMac, String deviceModel, String actionKey, Closure closure = {}) {
	logDebug("apiRunActionLegacy()")

	requestBody = wyzeRequestBody() + [
		'sv': '011a6b42d80a4f32b4cc24bb721c9c96',
		'action_key': actionKey,
		'action_params': [:],
		'instance_id': deviceMac,
		'provider_key': deviceModel
	]

	callbackData = [
		'deviceNetworkId': deviceMac,
		'propertyList': [
			[
				'pid': actionKey,
				'pvalue': actionKey
			]
		]
	]

	asyncapiPost('/app/v2/auto/run_action', requestBody, 'deviceEventsCallback', callbackData)
}

private def apiRunActionDeviceMgmt(String deviceMac, String deviceModel, String actionKey, Closure closure = {}) {
	logDebug("apiRunActionDeviceMgmt()")

	// Map legacy action keys to devicemgmt capability values
	String capabilityValue
	if (actionKey == 'power_on') {
		capabilityValue = 'wakeup'
	} else if (actionKey == 'power_off') {
		capabilityValue = 'sleep'
	} else {
		logWarn("apiRunActionDeviceMgmt: unsupported actionKey '${actionKey}', falling back to legacy API")
		apiRunActionLegacy(deviceMac, deviceModel, actionKey, closure)
		return
	}

	// Payload format per wyzeapy reference implementation
	Map capability = [
		'functions': [
			['in': ['wakeup-live-view': '1'], 'name': capabilityValue]
		],
		'iid': 1,
		'name': 'iot-device'
	]

	requestBody = [
		'capabilities': [capability],
		'nonce': (new Date()).getTime(),
		'targetInfo': [
			'id': deviceMac,
			'productModel': deviceModel,
			'type': 'DEVICE'
		],
		'transactionId': '0a5b20591fedd4du1b93f90743ba0csd'
	]

	// Map back to standard property format for the callback
	callbackData = [
		'deviceNetworkId': deviceMac,
		'propertyList': [
			[
				'pid': actionKey,
				'pvalue': actionKey
			]
		]
	]

	logInfo("apiRunActionDeviceMgmt: sending power=${capabilityValue} for ${deviceMac}")
	asyncapiPostDeviceMgmt('/device-management/api/action/run_action', requestBody, 'deviceEventsCallback', callbackData)
}

def apiRunActionList(String deviceMac, String deviceModel, List actionList) {
	logDebug("apiRunActionList()")
	logDebug(['mac': deviceMac, 'model': deviceModel, 'actionList': actionList])

	List apiActionList = [
		[
			'action_key': 'set_mesh_property',
			'instance_id': deviceMac,
			'provider_key': deviceModel,
			'action_params': [
				'list': [
						[
							'mac': deviceMac,
							'plist': actionList
						]
					]
			]
		]
	]

	requestBody = wyzeRequestBody() + [
		'sv': '5e02224ae0c64d328154737602d28833', 
		'action_list': apiActionList
	]

	callbackData = [
		'deviceNetworkId': deviceMac,
		'propertyList': actionList
	]

	asyncapiPost('/app/v2/auto/run_action_list', requestBody, 'deviceEventsCallback', callbackData)
}

def apiSetDeviceProperty(String deviceMac, String deviceModel, String propertyId, value) {
	logDebug("setDeviceProperty()")
	logDebug(['mac': deviceMac, 'model': deviceModel, 'propertyId': propertyId, 'value': value])

	requestBody = wyzeRequestBody() + [
		'sv': '44b6d5640c4d4978baba65c8ab9a6d6e',
		'device_mac': deviceMac,
		'device_model': deviceModel, 
		'pid': propertyId,
		'pvalue': value
	]

	callbackData = [
		'deviceNetworkId': deviceMac,
		'propertyList': [
			[
				'pid': propertyId,
				'pvalue': value
			]
		]
	]
	
	asyncapiPost('/app/v2/device/set_property', requestBody, 'deviceEventsCallback', callbackData)
}

def asyncapiPost(String path, Map body = [:], String callbackMethod = null, Map callbackData = [:]) {
	logDebug('asyncapiPost()')

	bodyJson = (new JsonBuilder(body)).toString()

    params = [
		'uri'         : wyzeApiBaseUrl(),
		'headers'     : wyzeRequestHeaders(),
		'contentType' : 'application/json',
		'path'        : path,
		'body'        : bodyJson
	]

	asynchttpPost(callbackMethod, params, callbackData)
}

def asyncapiPostDeviceMgmt(String path, Map body = [:], String callbackMethod = null, Map callbackData = [:]) {
	logDebug('asyncapiPostDeviceMgmt()')
	bodyJson = (new JsonBuilder(body)).toString()

	params = [
		'uri'         : wyzeDeviceMgmtBaseUrl(),
		'headers'     : [
			'authorization': state.access_token,
			'Content-Type': 'application/json'
		],
		'contentType' : 'application/json',
		'path'        : path,
		'body'        : bodyJson
	]

	asynchttpPost(callbackMethod, params, callbackData)
}

def apiPost(String path, Map body = [], Closure closure = {}) {
	logDebug('apiPost()')

	if (!(body.access_token || body.refresh_token)) {
		throw new Exception('No Auth Tokens. Reauthenticate.')
	}

	bodyJson = (new JsonBuilder(body)).toString()

    params = [
		'uri'         : wyzeApiBaseUrl(),
		'headers'     : wyzeRequestHeaders(),
		'contentType' : 'application/json',
		'path'        : path,
		'body'        : bodyJson
	]

	try {
		httpPost(params) { response -> 
			validateApiResponse(response)
			closure(response.data) 
		}
	} catch (Exception e) {
		logError("API Call to ${params.uri}${params.path} failed with Exception: ${e}")
	}
}

private validateApiResponse(response) {
	logDebug("validateApiResponse()")
	
	if (response.hasProperty('message')) {
		// i.e. 'Rate limit is exceeded.'
		// TODO do something
		logError(response.message)
		throw new Exception(response.message)
	}
	
	if (response.data instanceof String) {
		responseData = parseJson(response.data)
	} else {
		responseData = response.data
	}

	// Normalize code to string for comparison (API may return integer or string)
	String codeStr = responseData.code?.toString()

	if (codeStr == "2001") {
		logError("Access Token Invalid. Attempting to refresh token.")
		logDebug(response.data)
		refreshAccessToken() { refreshTokenResponse ->
			return false
		}
	}

	if (codeStr == "2002") {
		// Refresh Token Error
		logError("Refresh Token Invalid.")
		clearState()
		throw new Exception("Refresh Token Invalid")
	}

	if (codeStr != "1") {
		logError("API Response error!")
		logDebug(response.data)
		throw new Exception("Invalid Response Data Code: ${response.data}")
	}

	return true
}

private Boolean validateAsyncApiResponse(response) {
	logDebug("validateAsyncApiResponse()")

	if (response.hasError()) {
		logError("Async API response has error: status=${response.status}, errorMessage=${response.getErrorMessage()}")
		if (response.status == 401) {
			logError("Access Token expired (HTTP 401). Attempting to refresh token.")
			refreshAccessToken()
		}
		return false
	}

	if (!response.getData()) {
		logError("Async API response has no data")
		return false
	}

	try {
		responseData = parseJson(response.getData())
	} catch (Exception e) {
		logError("Failed to parse async API response JSON: ${e}")
		return false
	}

	// Normalize code to string for comparison (API may return integer or string)
	String codeStr = responseData.code?.toString()

	if (codeStr == "2001") {
		logError("Access Token Invalid (async). Attempting to refresh token.")
		refreshAccessToken()
		return false
	}

	if (codeStr == "2002") {
		logError("Refresh Token Invalid (async).")
		clearState()
		return false
	}

	// Device management API responses may not include a code field
	if (codeStr != null && codeStr != "1") {
		logError("Async API response error: code=${codeStr}, msg=${responseData.msg}")
		logDebug("Full response data: ${response.getData()}")
		return false
	}

	return true
}

private scheduleTokenRefresh() {
	unschedule('scheduledTokenRefresh')
	String interval = settings.tokenRefreshInterval ?: '360'
	switch (interval) {
		case '30':
			runEvery30Minutes('scheduledTokenRefresh')
			logInfo('Token refresh scheduled every 30 minutes')
			break
		case '60':
			runEvery1Hour('scheduledTokenRefresh')
			logInfo('Token refresh scheduled every 1 hour')
			break
		case '180':
			runEvery3Hours('scheduledTokenRefresh')
			logInfo('Token refresh scheduled every 3 hours')
			break
		case '360':
			schedule("0 0 */6 ? * *", 'scheduledTokenRefresh')
			logInfo('Token refresh scheduled every 6 hours')
			break
		case '720':
			schedule("0 0 */12 ? * *", 'scheduledTokenRefresh')
			logInfo('Token refresh scheduled every 12 hours')
			break
		default:
			logInfo('Periodic token refresh disabled')
			break
	}
}

def scheduledTokenRefresh() {
	logInfo('Running scheduled token refresh')
	refreshAccessToken()
}

private refreshAccessToken(Closure closure = {}) {
	logDebug('refreshAccessToken()')

	if (state.refreshingToken) {
		logDebug('Token refresh already in progress. Skipping.')
		return
	}

	state.refreshingToken = true

	requestBody = wyzeRequestBody() + [
		'sv': 'd91914dd28b7492ab9dd17f7707d35a3',
		'refresh_token': state.refresh_token
	]
	requestBody['access_token'] = null

	try {
		apiPost('/app/user/refresh_token', requestBody) { response ->
			logDebug("refreshToken Resposne:")
			logDebug(response.data)
			state.access_token = response.data.access_token
			state.refresh_token = response.data.refresh_token
			closure(response)
		}
	} finally {
		state.refreshingToken = false
	}
}

private void deviceMgmtPropertyCallback(response, data) {
	logDebug("deviceMgmtPropertyCallback() for device ${data?.deviceNetworkId}")

	try {
		if (!validateAsyncApiResponse(response)) {
			return
		}

		responseData = parseJson(response.getData())

		// Parse devicemgmt capabilities response into standard property list format
		List propertyList = []
		List capabilities = responseData?.data?.capabilities ?: responseData?.capabilities ?: []

		capabilities.each { capability ->
			String capName = capability.name
			Map props = capability.properties ?: [:]

			if (capName == 'iot-device') {
				// iot-power maps to switch state
				if (props.containsKey('iot-power')) {
					String powerValue = props['iot-power']?.toString()
					Boolean powerOn = (powerValue == '1' || powerValue == 'true')
					propertyList << [
						'pid': 'power',
						'pvalue': powerOn ? 'power_on' : 'power_off'
					]
				}
				// iot-state maps to online/available
				if (props.containsKey('iot-state')) {
					String stateValue = props['iot-state']?.toString()
					String normalizedState = (stateValue == '1' || stateValue == 'true') ? '1' : '0'
					propertyList << ['pid': 'P5', 'pvalue': normalizedState]
				}
				// push-switch maps to notifications
				if (props.containsKey('push-switch')) {
					String pushValue = props['push-switch']?.toString()
					String normalizedPush = (pushValue == '1' || pushValue == 'true') ? '1' : '0'
					propertyList << ['pid': 'P1', 'pvalue': normalizedPush]
				}
			} else if (capName == 'camera') {
				if (props.containsKey('motion-detect-recording')) {
					String recordValue = props['motion-detect-recording']?.toString()
					String normalizedRecord = (recordValue == '1' || recordValue == 'true') ? '1' : '0'
					propertyList << ['pid': 'P1001', 'pvalue': normalizedRecord]
				}
			}
		}

		if (!(data?.deviceNetworkId && propertyList)) {
			return
		}

		parentNetworkId = state.deviceParentMap[data.deviceNetworkId]
		if (parentNetworkId) {
			parentDevice = getChildDevice(parentNetworkId)
			device = parentDevice?.getChildDevice(data.deviceNetworkId)
		} else {
			device = getChildDevice(data.deviceNetworkId)
		}

		if (!device) {
			logDebug("Device ${data.deviceNetworkId} not found")
			return
		}

		device.createDeviceEventsFromPropertyList(propertyList)
	} catch (Exception e) {
		logError("deviceMgmtPropertyCallback exception for device ${data?.deviceNetworkId}: ${e}")
	}
}

private void deviceEventsCallback(response, data) {
	logDebug("deviceEventsCallback() for device ${data?.deviceNetworkId}")

	try {
		if (!validateAsyncApiResponse(response)) {
			return
		}

		responseData = parseJson(response.getData())
		propertyList = data?.propertyList ?: responseData?.data?.property_list ?: []

		if (!(data?.deviceNetworkId && propertyList)) {
			return
		}

		parentNetworkId = state.deviceParentMap[data.deviceNetworkId]
		if (parentNetworkId) {
			parentDevice = getChildDevice(parentNetworkId)
			device = parentDevice?.getChildDevice(data.deviceNetworkId)
		} else {
			device = getChildDevice(data.deviceNetworkId)
		}

		if (!device) {
			logDebug("Device ${data.deviceNetworkId} not found")
			return
		}

		device.createDeviceEventsFromPropertyList(propertyList)
	} catch (Exception e) {
		logError("deviceEventsCallback exception for device ${data?.deviceNetworkId}: ${e}")
	}
}

private void deviceEventValueCallback(response, data) {
	logDebug("deviceEventValueCallback() for device ${data?.deviceNetworkId}")

	try {
		if (!validateAsyncApiResponse(response)) {
			return
		}

		responseData = parseJson(response.getData())

		eventList = data?.event_list ?: responseData?.data?.event_list ?: []

		if (!data?.deviceNetworkId) {
			logDebug('Missing deviceNetworkId')
			return
		}

		if (!eventList) {
			logDebug("Event List Not Found - ${data.deviceNetworkId}")
			return
		}

		parentNetworkId = state.deviceParentMap[data.deviceNetworkId]
		if (parentNetworkId) {
			parentDevice = getChildDevice(parentNetworkId)
			device = parentDevice?.getChildDevice(data.deviceNetworkId)
		} else {
			device = getChildDevice(data.deviceNetworkId)
		}

		if (!device) {
			logDebug("Device ${data.deviceNetworkId} not found")
			return
		}

		device.createDeviceEventsFromEventList(eventList)
	} catch (Exception e) {
		logError("deviceEventValueCallback exception for device ${data?.deviceNetworkId}: ${e}")
	}
}

private String getPhoneId() {
	if (!state.phone_id) {
		state.phone_id = randomUUID() as String
	}
	return state.phone_id
}

private String generateGroupNetworkId(Map wyzeGroupDetails) {
    return wyzeGroupDetails.group_type_id + '.' + wyzeGroupDetails.group_id
}

private String hashPassword(String password) {
	return md5(md5(md5(password)))
}

private String md5(String str) {
	return MessageDigest.getInstance("MD5").digest(str.bytes).encodeHex().toString()
}

private clearState() {
	state.access_token   = null
	state.refresh_token  = null
	state.user_id        = null
	state.mfa_options 	 = null
	state.mfa_details 	 = null
	state.sms_session_id = null
	state.statusText     = null
}

void appButtonHandler(btn) {
   switch(btn) {
      case "btnDeviceRefresh":
         // Just want to resubmit page, so nothing
         break        
      default:
         log.warn "Unhandled app button press: $btn"
   }
}

// def debugOff()
// {
// 	logWarn("Debug logging disabled...")
// 	app?.updateSetting("debugEnabled",[value:"false",type:"bool"])
// }

private void logDebug(message) {
	level = settings.logLevel ?: log_level_default
	if (level >= log_level_debug) {
		log.debug("[${app.label}] " + message)
	}
}

private void logInfo(message) {
	level = settings.logLevel ?: log_level_default
	if (level >= log_level_info) {
		log.info("[${app.label}] " + message)
	}
}

private void logWarn(message) {
	level = settings.logLevel ?: log_level_default
	if (level >= log_level_warn) {
		log.warn("[${app.label}] " + message)
	}
}

private void logError(message) {
	level = settings.logLevel ?: log_level_default
	if (level >= log_level_error) {
		log.error("[${app.label}] " + message)
	}
}
