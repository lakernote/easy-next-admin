package com.laker.admin.infrastructure.web.mvc;

import java.io.Serializable;

/**
 * @author easynext
 */
public interface IEnum<T extends Serializable> {
    T getValue();
}
