metadata {
	definition (name: "Seluxit Pulse Meter", namespace: "esbenbach", author: "Esben Bach") {
		capability "Power Meter" /* capability.powerMeter */
		capability "Health Check"
		capability "Configuration"
        //capability "Polling"
        //capability "Refresh"

		command "reset"
		command "getConfiguration"

		attribute "rawPulseCount", "number"

        // 	zw:L type:3000 mfr:0069 prod:0005 model:0002 ver:1.00 zwv:2.40 lib:06 cc:35,31,72,86,60,70,85,8E epc:4
		fingerprint mfr: "0069", prod: "0005", model: "0002", deviceJoinName: "Seluxit Pulse Meter", vid: "pulse-meter-4", inClusters: "0x35, 0x31, 0x72, 0x86, 0x60, 0x70, 0x85, 0x8E"
	}

	simulator {
		// Undefined
	}

    // Random stolen
	tiles(scale: 2) {
		multiAttributeTile(name:"power", type: "generic", width: 6, height: 4){
			tileAttribute("device.power", key: "PRIMARY_CONTROL") {
				attributeState("default", label:'${currentValue} W')
			}
		}
		standardTile("reset", "device.energy", inactiveLabel: false, decoration: "flat", width: 2, height: 2) {
			state "default", label:'reset kWh', action:"reset"
		}

		main (["power","energy"])
		details(["power","energy", "configure", "getConfiguration", "reset", "refresh"])
	}
}

// Apparently called when first installed
def installed() {
	log.debug "Installed ${device.displayName}"
	sendEvent(name: "checkInterval", value: 1800, displayed: true, data: [protocol: "zwave", hubHardwareId: device.hub.hardwareID, offlinePingable: "1"])
	configure()
}

def updated()
{
	log.debug "Updated ${device.displayName}"
	sendEvent(name: "checkInterval", value: 1800, displayed: true, data: [protocol: "zwave", hubHardwareId: device.hub.hardwareID, offlinePingable: "1"])
}


def refresh()
{
    log.debug "Refreshing"
}

// If you add the Polling capability to your device type, this command will be called approximately every 5 minutes to check the device's state
def poll()
{
    log.debug "Polling"
	
	return delayBetween([
		//encap(zwave.multiChannelV3.multiChannelEndPointGet()), // This one ends up installing child devices...
		encap(zwave.sensorMultilevelV5.sensorMultilevelGet()),
	], 500)
	
}

// If you add the Healthcheck capability this command will be called if Smartthings thinks the device is offline (checkinterval exceeded i think)
def ping() {
	refresh()
	return createEvent(name: "healthCheck", value: "online")
}

// Reset stuff
def reset() {
	log.debug "Reset called, not really doing anything"
    // Consider reseting the pulse count parameter (config)
}

// Parse Events received from the Device and convert them into data (events/attributes) for the SmartThings Platform.
def parse(String description) {
	log.debug "Parsing '${description}'"
    def result = null
    def cmd = zwave.parse(description)
    if (cmd) {
        result = zwaveEvent(cmd)
        log.debug "Parsed event ${description} into inspected instance ${result.inspect()}"
    } else {
        log.debug "Non-parsed event: ${description}"
    }
    
    return result
}

// Generic catch all handler not really sure its ever called
def zwaveEvent(physicalgraph.zwave.Command cmd) {
	log.debug "Received Generic Command"
	return createEvent(descriptionText: "$device.displayName: $cmd", isStateChange: true)
}

def zwaveEvent(physicalgraph.zwave.commands.configurationv1.ConfigurationReport cmd) {	 
	log.debug "Recieved Configuration report ${cmd.configurationValue}"
	return createEvent(descriptionText: "$device.displayName: $cmd", isStateChange: true)
}

def zwaveEvent(physicalgraph.zwave.commands.meterpulsev1.MeterPulseGet cmd)
{
    log.debug "Received MeterPulse Get Command: ${cmd.format()}"
}

def zwaveEvent(physicalgraph.zwave.commands.meterpulsev1.MeterPulseReport cmd)
{
    log.debug "Received Meterpulse Report: ${cmd.format()} Pulse count: ${cmd.pulseCount}"
	createEvent(name: "rawPulseCount", value: cmd.pulseCount)
}

def zwaveEvent(physicalgraph.zwave.commands.sensormultilevelv5.SensorMultilevelGet cmd)
{
    log.debug "Sensor Multi Level 5 Get: ${cmd.format()}"
}

def zwaveEvent(physicalgraph.zwave.commands.sensormultilevelv5.SensorMultilevelReport cmd)
{
    log.debug "Sensor Multi Level 5 Report: ${cmd.format()} - Sensor Type ${cmd.sensorType} - ${cmd.sensorValue}"
    createEvent(name: "power", value: Math.round(cmd.scaledSensorValue), unit: "W")	
}

def zwaveEvent(physicalgraph.zwave.commands.multichannelv3.MultiChannelEndPointReport cmd, ep = null) 
{
	log.debug "Multichannel Endpoint Report ${cmd.format()}"
	log.debug "${cmd.endPoints}";
}

def zwaveEvent(physicalgraph.zwave.commands.multichannelv3.MultiChannelCapabilityReport cmd)
{
	log.debug "Multichannel Capability Report ${cmd.format()}"
	def result = []
	def cmds = []
	if(!state.endpointInfo) state.endpointInfo = []
	state.endpointInfo[cmd.endPoint - 1] = cmd.format()[6..-1]
	if (cmd.endPoint < getDataValue("endpoints").toInteger()) {
		cmds = zwave.multiChannelV3.multiChannelCapabilityGet(endPoint: cmd.endPoint + 1).format()
	} else {
		log.debug "endpointInfo: ${state.endpointInfo.inspect()}"
	}
	result << createEvent(name: "epInfo", value: util.toJson(state.endpointInfo), displayed: false, descriptionText:"")
	if(cmds) result << response(cmds)
	return result
}

// If you add the Configuration capability to your device type, this command will be called right
// after the device joins to set device-specific configuration commands.
def configure() 
{
        log.debug "Configuring Seluxit Pulse Meter"
		
        delayBetween([
                // Note that configurationSet.size is 1, 2, or 4 and generally must match the size the device uses in its configurationReport
				
				// Configure S0.1
                zwave.configurationV1.configurationSet(parameterNumber:2, size:4, scaledConfigurationValue:1000).format(), // Pulses per kwh
                zwave.configurationV1.configurationSet(parameterNumber:6, size:1, scaledConfigurationValue:4).format(), // Sensor Type
                zwave.configurationV1.configurationSet(parameterNumber:30, size:4, scaledConfigurationValue:0).format(), // Reset pulse counts
				zwave.configurationV1.configurationSet(parameterNumber:14, size:4, scaledConfigurationValue:1000).format(), // Number of pulses before Group 1 Report
				zwave.configurationV1.configurationSet(parameterNumber:22, size:4, scaledConfigurationValue:300).format(), // Seconds before Group 1 Report
				
				// Configure S0.2
				/*zwave.configurationV1.configurationSet(parameterNumber:3, size:4, scaledConfigurationValue:1000).format(), // Pulses per kwh
				zwave.configurationV1.configurationSet(parameterNumber:7, size:1, scaledConfigurationValue:4).format(), // Sensor Type
				zwave.configurationV1.configurationSet(parameterNumber:31, size:4, scaledConfigurationValue:0).format(), // Reset pulse counts
				zwave.configurationV1.configurationSet(parameterNumber:15, size:4, scaledConfigurationValue:1000).format(), // Number of pulses before Group 1 Report
				zwave.configurationV1.configurationSet(parameterNumber:23, size:4, scaledConfigurationValue:300).format(), // Seconds before Group 1 Report

				// Configure S0.3
				zwave.configurationV1.configurationSet(parameterNumber:4, size:4, scaledConfigurationValue:1000).format(), // Pulses per kwh
				zwave.configurationV1.configurationSet(parameterNumber:8, size:1, scaledConfigurationValue:4).format(), // Sensor Type
				zwave.configurationV1.configurationSet(parameterNumber:32, size:4, scaledConfigurationValue:0).format(), // Reset pulse counts
				zwave.configurationV1.configurationSet(parameterNumber:16, size:4, scaledConfigurationValue:1000).format(), // Number of pulses before Group 1 Report
				zwave.configurationV1.configurationSet(parameterNumber:24, size:4, scaledConfigurationValue:300).format(), // Seconds before Group 1 Report

				// Configure S0.3
				zwave.configurationV1.configurationSet(parameterNumber:5, size:4, scaledConfigurationValue:1000).format(), // Pulses per kwh
				zwave.configurationV1.configurationSet(parameterNumber:9, size:1, scaledConfigurationValue:4).format(), // Sensor Type
				zwave.configurationV1.configurationSet(parameterNumber:33, size:4, scaledConfigurationValue:0).format(), // Reset pulse counts
				zwave.configurationV1.configurationSet(parameterNumber:17, size:4, scaledConfigurationValue:1000).format(), // Number of pulses before Group 1 Report
				zwave.configurationV1.configurationSet(parameterNumber:25, size:4, scaledConfigurationValue:300).format(), // Seconds before Group 1 Report*/
                
                // Subscribe to device automatic reports (association group 1 and 2)
				encap(zwave.associationV1.associationSet(groupingIdentifier:1, nodeId:zwaveHubNodeId)),
                encap(zwave.associationV1.associationSet(groupingIdentifier:2, nodeId:zwaveHubNodeId))


        ], 200)
}


def getConfiguration()
{
	delayBetween([
		// Pulses per Unit sent?
		zwave.configurationV1.configurationGet(parameterNumber:2).format(),
		zwave.configurationV1.configurationGet(parameterNumber:3).format(),
		zwave.configurationV1.configurationGet(parameterNumber:4).format(),
		zwave.configurationV1.configurationGet(parameterNumber:5).format(),

		// Sensor Type
		zwave.configurationV1.configurationGet(parameterNumber:6).format(),
		zwave.configurationV1.configurationGet(parameterNumber:7).format(),
        zwave.configurationV1.configurationGet(parameterNumber:8).format(),
        zwave.configurationV1.configurationGet(parameterNumber:9).format(),
        
		// Pulse Count
		zwave.configurationV1.configurationGet(parameterNumber:30).format(),
		zwave.configurationV1.configurationGet(parameterNumber:31).format(),
		zwave.configurationV1.configurationGet(parameterNumber:32).format(),
		zwave.configurationV1.configurationGet(parameterNumber:33).format(),

		// Association groups
        zwave.associationV1.associationGet(groupingIdentifier:1).format(),
        zwave.associationV1.associationGet(groupingIdentifier:2).format(),

		// Unknown - probably time between "sensor_multilevel_report" on association group 2
		zwave.configurationV1.configurationGet(parameterNumber:26).format(),
		zwave.configurationV1.configurationGet(parameterNumber:27).format(),
		zwave.configurationV1.configurationGet(parameterNumber:28).format(),
		zwave.configurationV1.configurationGet(parameterNumber:29).format()
        
		], 500)
}


private encap(cmd, endpoint = null) {
	if (cmd) {
		if (endpoint) {
			cmd = zwave.multiChannelV3.multiChannelCmdEncap(destinationEndPoint: endpoint).encapsulate(cmd)
		}
		cmd.format()
	}
}

def deviceIncludesMeter()
{
	return true
}