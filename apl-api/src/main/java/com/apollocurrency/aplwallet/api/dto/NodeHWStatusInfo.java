/*
 * Copyright © 2018-2019 Apollo Foundation
 */
package com.apollocurrency.aplwallet.api.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 *
 * @author alukin@gmail.com
 */

@Schema(name="BackStatusInfo", description="Information about backend state")
@Getter @Setter @ToString
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public class NodeHWStatusInfo {
    @Schema(name="Number of CPU", description="Number of active CPU cores")
    public Integer cpuCores;
    @Schema(name="Average CPU load", description="Current average CPU load for all cores")
    public Double cpuLoad;
    @Schema(name="Total memory", description="Tottal memory in bytes")
    public Long memoryTotal;
    @Schema(name="Free memory", description="Free memory available for this application")
    public Long memoryFree;
    @Schema(name="Free disk space", description="Free disk space available to applicaion")
    public Long diskFree;    
}
