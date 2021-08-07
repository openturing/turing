import React, { useRef, useEffect } from 'react';
import PropTypes from 'prop-types';
import jdenticon from 'jdenticon';

const VigJdenticon = ({ value = 'test', size = '100%' }) => {
  const icon = useRef(null);
  useEffect(() => {
    jdenticon.update(icon.current, value);
  }, [value]);
  var iconStyle={
    display: 'inline-block',
    userSelect:'none',
    verticalAlign: 'text-bottom'
  }
  return (
    <svg data-jdenticon-value={value} height={size} ref={icon} width={size} style={iconStyle} />
  );
};

VigJdenticon.propTypes = {
  size: PropTypes.string,
  value: PropTypes.string.isRequired
};
export default VigJdenticon;