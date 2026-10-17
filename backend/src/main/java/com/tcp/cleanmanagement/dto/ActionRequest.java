package com.tcp.cleanmanagement.dto;

import lombok.Data;
import java.util.List;

@Data
public class ActionRequest {
    private String actionDetail;
    private Long adminId;
    private List<Long> zoneIds;
    private List<Long> alertIds;
}
