package com.pipemasters.assets;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({
        "name",
        "icon"
})
public record AssetPriority(String name,
                            String icon) {
}